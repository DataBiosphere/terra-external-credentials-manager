package bio.terra.externalcreds.services;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.BondDatastoreDAO;
import bio.terra.externalcreds.dataAccess.DistributedLockDAO;
import bio.terra.externalcreds.dataAccess.FenceAccountKeyDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.BondFenceServiceAccountEntity;
import bio.terra.externalcreds.models.DistributedLock;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.LinkedAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class FenceProviderService extends ProviderService {

  private final LinkedAccountDAO linkedAccountDAO;
  private final FenceAccountKeyDAO fenceAccountKeyDAO;
  private final DistributedLockDAO distributedLockDAO;
  private final BondDatastoreDAO bondDatastoreDAO;

  public FenceProviderService(
      ExternalCredsConfig externalCredsConfig,
      ProviderOAuthClientCache providerOAuthClientCache,
      ProviderTokenClientCache providerTokenClientCache,
      OAuth2Service oAuth2Service,
      LinkedAccountService linkedAccountService,
      LinkedAccountDAO linkedAccountDAO,
      FenceAccountKeyDAO fenceAccountKeyDAO,
      DistributedLockDAO distributedLockDAO,
      BondDatastoreDAO bondDatastoreDAO,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    super(
        externalCredsConfig,
        providerOAuthClientCache,
        providerTokenClientCache,
        oAuth2Service,
        linkedAccountService,
        auditLogger,
        objectMapper);
    this.linkedAccountDAO = linkedAccountDAO;
    this.fenceAccountKeyDAO = fenceAccountKeyDAO;
    this.distributedLockDAO = distributedLockDAO;
    this.bondDatastoreDAO = bondDatastoreDAO;
  }

  public Optional<LinkedAccount> getLinkedFenceAccount(String userId, Provider provider) {
    var ecmLinkedAccount = linkedAccountDAO.getLinkedAccount(userId, provider);
    var bondLinkedAccount = getBondLinkedAccount(userId, provider);
    if (ecmLinkedAccount.isPresent() && bondLinkedAccount.isPresent()) {
      var ecmExpires = ecmLinkedAccount.get().getExpires();
      var bondExpires = bondLinkedAccount.get().getExpires();
      if (ecmExpires.after(bondExpires) || ecmExpires.equals(bondExpires)) {
        // ECM is up to date
        return ecmLinkedAccount;
      } else {
        // ECM is out of date. Update it with the Bond data and return the new ECM Linked Account
        return updateEcmWithBondInfo(bondLinkedAccount.get());
      }
    }
    // ECM is out of date. Port the Bond data into the ECM and return the new ECM Linked Account
    return bondLinkedAccount.map(this::updateEcmWithBondInfo).orElse(ecmLinkedAccount);
  }

  public Optional<FenceAccountKey> getLinkedFenceAccountKey(String userId, Provider provider) {
    var linkedAccount = getLinkedFenceAccount(userId, provider);
    return linkedAccount
        .flatMap(fenceAccountKeyDAO::getFenceAccountKey)
        .or(() -> linkedAccount.flatMap(this::createFenceAccountKey));
  }

  private Optional<FenceAccountKey> createFenceAccountKey(LinkedAccount linkedAccount) {
    var lockName =
        String.format("%s-retrieveFenceAccountKey", linkedAccount.getProvider().toString());
    Optional<DistributedLock> maybeLock =
        distributedLockDAO.getDistributedLock(lockName, linkedAccount.getUserId());
    if (maybeLock.isPresent()) {
      // If the lock is present, another thread/instance is getting retrieving a key.
      // Wait for it to finish and get the key from the DB.
      while (maybeLock.isPresent()) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          log.error("Thread interrupted while waiting for lock", e);
        }
        maybeLock = distributedLockDAO.getDistributedLock(lockName, linkedAccount.getUserId());
      }
      return fenceAccountKeyDAO.getFenceAccountKey(linkedAccount);
    } else {
      // Lock is not present.
      // Create a lock and retrieve a key from the fence provider.
      var newLock =
          new DistributedLock.Builder()
              .lockName(lockName)
              .userId(linkedAccount.getUserId())
              .expiresAt(Instant.now().plus(1, ChronoUnit.MINUTES))
              .build();
      try {
        distributedLockDAO.insertDistributedLock(newLock);
      } catch (DataAccessException e) {
        log.warn(
            String.format(
                "Failed to insert lock %s, another thread is doing the same thing.", lockName),
            e);
        // Just recursively call this function. Just return the key created by the other thread.
        return getLinkedFenceAccountKey(linkedAccount.getUserId(), linkedAccount.getProvider());
      }
      var fenceAccountKey = retrieveFenceAccountKey(linkedAccount);
      fenceAccountKeyDAO.upsertFenceAccountKey(fenceAccountKey);
      distributedLockDAO.deleteDistributedLock(lockName, linkedAccount.getUserId());
      return Optional.of(fenceAccountKey);
    }
  }

  private FenceAccountKey retrieveFenceAccountKey(LinkedAccount linkedAccount) {
    var providerClient = providerOAuthClientCache.getProviderClient(linkedAccount.getProvider());
    var providerProperties = externalCredsConfig.getProviderProperties(linkedAccount.getProvider());
    var accessToken =
        oAuth2Service.authorizeWithRefreshToken(
            providerClient, new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null));
    var keyEndpoint = providerProperties.getKeyEndpoint();
    WebClient.ResponseSpec response =
        WebClient.create(keyEndpoint.get())
            .post()
            .header("Authorization", "Bearer " + accessToken.getAccessToken().getTokenValue())
            .retrieve();
    String responseBody =
        response
            .onStatus(HttpStatusCode::isError, clientResponse -> Mono.empty())
            .bodyToMono(String.class)
            .block(Duration.of(11, ChronoUnit.SECONDS));
    return new FenceAccountKey.Builder()
        .linkedAccountId(linkedAccount.getId().get())
        .keyJson(responseBody)
        .expiresAt(
            Instant.now().plus(providerProperties.getLinkLifespan().toDays(), ChronoUnit.DAYS))
        .build();
  }

  private Optional<LinkedAccount> updateEcmWithBondInfo(LinkedAccount bondLinkedAccount) {
    var linkedAccount = linkedAccountDAO.upsertLinkedAccount(bondLinkedAccount);
    var bondFenceServiceAccountKey =
        bondDatastoreDAO.getFenceServiceAccountKey(
            bondLinkedAccount.getUserId(), bondLinkedAccount.getProvider());
    var fenceAccountKey =
        bondFenceServiceAccountKey.map(
            bondKey ->
                new FenceAccountKey.Builder()
                    .linkedAccountId(linkedAccount.getId().get())
                    .keyJson(bondKey.getKeyJson())
                    .expiresAt(bondKey.getExpiresAt())
                    .build());

    fenceAccountKey.ifPresent(key -> fenceAccountKeyDAO.upsertFenceAccountKey(key));
    return Optional.of(linkedAccount);
  }

  public Optional<LinkedAccount> getBondLinkedAccount(String userId, Provider provider) {
    var bondRefreshTokenEntity = bondDatastoreDAO.getRefreshToken(userId, provider);
    var providerProperties = externalCredsConfig.getProviderProperties(provider);
    var bondLinkedAccount =
        bondRefreshTokenEntity.map(
            refreshTokenEntity ->
                new LinkedAccount.Builder()
                    .provider(provider)
                    .userId(userId)
                    .expires(
                        new Timestamp(
                            refreshTokenEntity
                                .getIssuedAt()
                                .plus(
                                    providerProperties.getLinkLifespan().toDays(), ChronoUnit.DAYS)
                                .toEpochMilli()))
                    .externalUserId(refreshTokenEntity.getUsername())
                    .refreshToken(refreshTokenEntity.getToken())
                    .isAuthenticated(true)
                    .build());
    return bondLinkedAccount.map(linkedAccountService::upsertLinkedAccount);
  }

  public LinkedAccount createLink(
      Provider provider,
      String userId,
      String authorizationCode,
      String encodedState,
      AuditLogEvent.Builder auditLogEventBuilder) {
    var oAuth2State = validateOAuth2State(provider, userId, encodedState);
    var providerClient = providerOAuthClientCache.getProviderClient(provider);
    var providerInfo = externalCredsConfig.getProviderProperties(provider);
    try {
      var account =
          createLinkedAccount(
                  provider,
                  userId,
                  authorizationCode,
                  oAuth2State.getRedirectUri(),
                  new HashSet<>(providerInfo.getScopes()),
                  encodedState,
                  providerClient)
              .getLeft();
      var linkedAccount = linkedAccountService.upsertLinkedAccount(account);
      logLinkCreation(Optional.of(linkedAccount), auditLogEventBuilder);
      return linkedAccount;
    } catch (OAuth2AuthorizationException oauthEx) {
      logLinkCreation(Optional.empty(), auditLogEventBuilder);
      throw new BadRequestException(oauthEx);
    }
  }

  public Optional<BondFenceServiceAccountEntity> getBondFenceServiceAccountKey(
      String userId, Provider provider) {
    return bondDatastoreDAO.getFenceServiceAccountKey(userId, provider);
  }

  public void deleteBondLinkedAccount(String userId, Provider provider) {
    // TODO: We also need to revoke the refresh token
    bondDatastoreDAO.deleteRefreshToken(userId, provider);
    bondDatastoreDAO.deleteFenceServiceAccountKey(userId, provider);
  }

  public void logLinkCreation(
      Optional<LinkedAccount> linkedAccount, AuditLogEvent.Builder auditLogEventBuilder) {
    auditLogger.logEvent(
        auditLogEventBuilder
            .externalUserId(linkedAccount.map(LinkedAccount::getExternalUserId))
            .auditLogEventType(
                linkedAccount
                    .map(x -> AuditLogEventType.LinkCreated)
                    .orElse(AuditLogEventType.LinkCreationFailed))
            .build());
  }
}

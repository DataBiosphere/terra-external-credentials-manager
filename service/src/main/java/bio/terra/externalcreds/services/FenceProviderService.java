package bio.terra.externalcreds.services;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.LinkedAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FenceProviderService extends ProviderService {

  private final BondService bondService;
  private final FenceAccountKeyService fenceAccountKeyService;
  private final FenceKeyRetriever fenceKeyRetriever;

  public FenceProviderService(
      ExternalCredsConfig externalCredsConfig,
      ProviderOAuthClientCache providerOAuthClientCache,
      ProviderTokenClientCache providerTokenClientCache,
      OAuth2Service oAuth2Service,
      LinkedAccountService linkedAccountService,
      AuditLogger auditLogger,
      ObjectMapper objectMapper,
      BondService bondService,
      FenceAccountKeyService fenceAccountKeyService,
      FenceKeyRetriever fenceKeyRetriever) {
    super(
        externalCredsConfig,
        providerOAuthClientCache,
        providerTokenClientCache,
        oAuth2Service,
        linkedAccountService,
        fenceAccountKeyService,
        auditLogger,
        objectMapper);
    this.bondService = bondService;
    this.fenceAccountKeyService = fenceAccountKeyService;
    this.fenceKeyRetriever = fenceKeyRetriever;
  }

  public Optional<LinkedAccount> getLinkedFenceAccount(String userId, Provider provider) {
    var ecmLinkedAccount = linkedAccountService.getLinkedAccount(userId, provider);
    var bondLinkedAccount = bondService.getLinkedAccount(userId, provider);
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

  private Optional<LinkedAccount> updateEcmWithBondInfo(LinkedAccount bondLinkedAccount) {
    var linkedAccount = linkedAccountService.upsertLinkedAccount(bondLinkedAccount);
    var linkedAccountId =
        linkedAccount
            .getId()
            .orElseThrow(
                () -> new ExternalCredsException("Saved LinkedAccount did not get assigned an ID"));
    var fenceAccountKey =
        bondService.getFenceServiceAccountKey(
            linkedAccount.getUserId(), linkedAccount.getProvider(), linkedAccountId);

    fenceAccountKey.ifPresent(fenceAccountKeyService::upsertFenceAccountKey);
    return Optional.of(linkedAccount);
  }

  public Optional<FenceAccountKey> getFenceAccountKey(LinkedAccount linkedAccount) {
    return fenceKeyRetriever.getOrCreateFenceAccountKey(linkedAccount);
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

  public void deleteBondFenceLink(String userId, Provider provider) {
    bondService.deleteBondLinkedAccount(userId, provider);
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

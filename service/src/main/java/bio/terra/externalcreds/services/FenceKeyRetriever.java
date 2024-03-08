package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.DistributedLockDAO;
import bio.terra.externalcreds.exception.DistributedLockException;
import bio.terra.externalcreds.models.DistributedLock;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.LinkedAccount;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class FenceKeyRetriever {

  private final FenceAccountKeyService fenceAccountKeyService;
  private final DistributedLockDAO distributedLockDAO;
  private final ProviderOAuthClientCache providerOAuthClientCache;
  private final OAuth2Service oAuth2Service;
  private final ExternalCredsConfig externalCredsConfig;

  public FenceKeyRetriever(
      FenceAccountKeyService fenceAccountKeyService,
      DistributedLockDAO distributedLockDAO,
      ProviderOAuthClientCache providerOAuthClientCache,
      OAuth2Service oAuth2Service,
      ExternalCredsConfig externalCredsConfig) {
    this.fenceAccountKeyService = fenceAccountKeyService;
    this.distributedLockDAO = distributedLockDAO;
    this.providerOAuthClientCache = providerOAuthClientCache;
    this.oAuth2Service = oAuth2Service;
    this.externalCredsConfig = externalCredsConfig;
  }

  @Retryable(
      value = {DistributedLockException.class},
      maxAttemptsExpression = "${retry.getFenceAccountKey.maxAttempts}",
      backoff = @Backoff(delayExpression = "${retry.getFenceAccountKey.delay}"))
  public Optional<FenceAccountKey> createFenceAccountKey(LinkedAccount linkedAccount) {
    var maybeKey = fenceAccountKeyService.getFenceAccountKey(linkedAccount);
    return maybeKey.or(
        () -> {
          var newLock =
              new DistributedLock.Builder()
                  .lockName("createFenceKey-" + linkedAccount.getProvider())
                  .userId(linkedAccount.getUserId())
                  .expiresAt(Instant.now().plus(1, ChronoUnit.MINUTES))
                  .build();
          Optional<DistributedLock> maybeLock =
              distributedLockDAO.getDistributedLock(
                  newLock.getLockName(), linkedAccount.getUserId());
          if (maybeLock.isPresent()) {
            // If the lock is present, another thread/instance is getting retrieving a key.
            // Wait for it to finish and get the key from the DB.
            log.info("Lock {} is present, waiting for it to expire.", newLock.getLockName());
            throw new DistributedLockException(
                String.format(
                    "Encountered lock %s for user %s",
                    newLock.getLockName(), linkedAccount.getUserId()));
          } else {
            log.info("Lock {} is not present, creating a new lock.", newLock.getLockName());
            // Lock is not present.
            // Create a lock and retrieve a key from the fence provider.
            try {
              distributedLockDAO.insertDistributedLock(newLock);
            } catch (DataAccessException e) {
              log.warn(
                  String.format(
                      "Failed to insert lock %s, another thread is doing the same thing.",
                      newLock.getLockName()));
              throw new DistributedLockException(
                  String.format(
                      "Encountered lock %s for user %s",
                      newLock.getLockName(), linkedAccount.getUserId()));
            }
            var fenceAccountKey = retrieveFenceAccountKey(linkedAccount);
            fenceAccountKeyService.upsertFenceAccountKey(fenceAccountKey);
            distributedLockDAO.deleteDistributedLock(
                newLock.getLockName(), linkedAccount.getUserId());
            return Optional.of(fenceAccountKey);
          }
        });
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
}

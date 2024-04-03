package bio.terra.externalcreds.services;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.DistributedLockDAO;
import bio.terra.externalcreds.exception.DistributedLockException;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.DistributedLock;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.LinkedAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class FenceKeyRetriever {

  private final FenceAccountKeyService fenceAccountKeyService;
  private final DistributedLockDAO distributedLockDAO;
  private final AccessTokenCacheService accessTokenCacheService;
  private final ExternalCredsConfig externalCredsConfig;
  private final ObjectMapper objectMapper;

  public FenceKeyRetriever(
      FenceAccountKeyService fenceAccountKeyService,
      DistributedLockDAO distributedLockDAO,
      AccessTokenCacheService accessTokenCacheService,
      ExternalCredsConfig externalCredsConfig,
      ObjectMapper objectMapper) {
    this.fenceAccountKeyService = fenceAccountKeyService;
    this.distributedLockDAO = distributedLockDAO;
    this.accessTokenCacheService = accessTokenCacheService;
    this.externalCredsConfig = externalCredsConfig;
    this.objectMapper = objectMapper;
  }

  @Retryable(
      retryFor = {DistributedLockException.class},
      maxAttemptsExpression = "${retry.getFenceAccountKey.maxAttempts}",
      backoff = @Backoff(delayExpression = "${retry.getFenceAccountKey.delay}"))
  public Optional<FenceAccountKey> getOrCreateFenceAccountKey(LinkedAccount linkedAccount) {
    var maybeKey = fenceAccountKeyService.getFenceAccountKey(linkedAccount);
    return maybeKey.or(() -> retrieveNewKeyFromFence(linkedAccount));
  }

  private Optional<FenceAccountKey> retrieveNewKeyFromFence(LinkedAccount linkedAccount) {
    var newLock =
        new DistributedLock.Builder()
            .lockName("createFenceKey-" + linkedAccount.getProvider())
            .userId(linkedAccount.getUserId())
            .expiresAt(
                Instant.now()
                    .plus(externalCredsConfig.getDistributedLockConfiguration().getLockTimeout()))
            .build();
    Optional<DistributedLock> existingLock =
        distributedLockDAO.getDistributedLock(newLock.getLockName(), linkedAccount.getUserId());
    if (existingLock.isPresent()) {
      // If the lock is present, another thread/instance is getting retrieving a key.
      // Tigger a retry
      log.info("Lock {} is present, waiting for it to expire.", newLock.getLockName());
      throw new DistributedLockException(
          String.format(
              "Encountered lock %s for user %s", newLock.getLockName(), linkedAccount.getUserId()));
    } else {
      log.info(
          "Retrieving new {} Fence Account Key for user {}",
          linkedAccount.getProvider(),
          linkedAccount.getUserId());
      return obtainLockAndGetNewKey(newLock, linkedAccount);
    }
  }

  private Optional<FenceAccountKey> obtainLockAndGetNewKey(
      DistributedLock lock, LinkedAccount linkedAccount) {
    try {
      // Create a lock and retrieve a key from the fence provider.
      obtainLock(lock, linkedAccount);
      var fenceAccountKey = retrieveFenceAccountKey(linkedAccount);
      fenceAccountKeyService.upsertFenceAccountKey(fenceAccountKey);
      log.info("Removing lock {} for user {}", lock.getLockName(), linkedAccount.getUserId());
      distributedLockDAO.deleteDistributedLock(lock.getLockName(), linkedAccount.getUserId());
      return Optional.of(fenceAccountKey);
    } catch (Exception e) {
      log.error(
          "Failed to retrieve a new Fence Account Key for user {} with error: {}",
          linkedAccount.getUserId(),
          e.getMessage());
      distributedLockDAO.deleteDistributedLock(lock.getLockName(), linkedAccount.getUserId());
      throw new ExternalCredsException(
          "Failed to retrieve a new %s Fence Account Key for user %s"
              .formatted(linkedAccount.getProvider(), linkedAccount.getUserId()),
          e);
    }
  }

  private void obtainLock(DistributedLock newLock, LinkedAccount linkedAccount)
      throws DistributedLockException {
    try {
      log.info("Inserting lock {} for user {}", newLock.getLockName(), linkedAccount.getUserId());
      distributedLockDAO.insertDistributedLock(newLock);
    } catch (DataAccessException e) {
      log.warn(
          String.format(
              "Failed to insert lock %s, another thread is doing the same thing.",
              newLock.getLockName()));
      throw new DistributedLockException(
          String.format(
              "Encountered lock %s for user %s", newLock.getLockName(), linkedAccount.getUserId()));
    }
  }

  private FenceAccountKey retrieveFenceAccountKey(LinkedAccount linkedAccount) {
    if (linkedAccount.getId().isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot retrieved Fence Account Key for an unsaved Linked Account");
    }
    var providerProperties = externalCredsConfig.getProviderProperties(linkedAccount.getProvider());
    var accessToken =
        accessTokenCacheService.getLinkedAccountAccessToken(
            linkedAccount, new AuditLogEvent.Builder());
    var keyEndpoint = providerProperties.getKeyEndpoint();
    if (keyEndpoint.isEmpty()) {
      throw new IllegalArgumentException(
          "Provider " + linkedAccount.getProvider() + " does not have a key endpoint");
    }
    WebClient.ResponseSpec response =
        WebClient.create(keyEndpoint.get())
            .post()
            .header("Authorization", "Bearer " + accessToken)
            .retrieve();
    String responseBody =
        response
            .onStatus(HttpStatusCode::isError, clientResponse -> Mono.empty())
            .bodyToMono(String.class)
            .block(Duration.of(30, ChronoUnit.SECONDS));
    validateResponse(responseBody, linkedAccount.getProvider());
    return new FenceAccountKey.Builder()
        .linkedAccountId(linkedAccount.getId().get())
        .keyJson(responseBody)
        .expiresAt(
            Instant.now().plus(providerProperties.getLinkLifespan().toDays(), ChronoUnit.DAYS))
        .build();
  }

  private void validateResponse(String responseBody, Provider provider) {
    if (responseBody == null) {
      throw new ExternalCredsException(
          "Got a successful response from " + provider + ", but the body was empty.");
    }
    try {
      var jsonObject = objectMapper.readTree(responseBody);
      if (!jsonObject.has("client_email")) {
        log.error("Invalid Service Account JSON: {}", responseBody);
        throw new ExternalCredsException(
            "Failed to retrieve a new Fence Account Key from "
                + provider
                + ". Does not contain 'client_email' field!");
      }
    } catch (Exception e) {
      throw new ExternalCredsException("Failed to parse the JSON response from " + provider, e);
    }
  }
}

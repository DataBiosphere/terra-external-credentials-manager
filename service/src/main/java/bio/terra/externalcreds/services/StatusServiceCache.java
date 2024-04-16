package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.generated.model.SubsystemStatus;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.api.StatusApi;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Creating a provider client requires an api call to [provider
 * issuer]/.well-known/openid-configuration. That information almost never changes and we don't want
 * to hammer that api. Therefore this cache.
 *
 * <p>The cache is reset every 6 hours to detect infrequent changes.
 */
@Component
@Slf4j
public class StatusServiceCache {
  private final ProviderOAuthClientCache providerOAuthClientCache;
  private final ExternalCredsConfig externalCredsConfig;
  private final StatusApi samStatusApi;

  public StatusServiceCache(
      ProviderOAuthClientCache providerOAuthClientCache, ExternalCredsConfig externalCredsConfig) {
    this.providerOAuthClientCache = providerOAuthClientCache;
    this.externalCredsConfig = externalCredsConfig;
    this.samStatusApi = createSamStatusApi();
  }

  // Cache the status of each provider.
  // This needs to happen so that if a providerClient fails to get created, we don't hammer the
  // provider with requests to our own status endpoint.
  @Cacheable(cacheNames = "providerStatus", sync = true)
  public SubsystemStatus getProviderStatus(Provider provider) {
    var status = new SubsystemStatus();
    status.name(provider.toString());
    try {
      // When provider OAuth clients are created, they reach out to the provider to validate the
      // configuration. ECM caches these clients as to not completely hammer the provider.
      // If the OAuth client is in the cache, or a new one succeeds in being created, we assume
      // the provider is reachable.
      providerOAuthClientCache.getProviderClient(provider);
      status.ok(true);
    } catch (Exception e) {
      log.warn("Error checking provider %s status".formatted(provider), e);
      status.ok(false);
      status.addMessagesItem(e.getMessage());
    }
    return status;
  }

  // Same thing for Sam.
  @Cacheable(cacheNames = "samStatus", sync = true)
  public SubsystemStatus getSamStatus() {
    var status = new SubsystemStatus();
    status.name("sam");
    try {
      var samStatus = samStatusApi.getSystemStatus();
      status.ok(samStatus.getOk());
    } catch (Exception e) {
      log.warn("Error checking Sam status", e);
      status.ok(false);
      status.addMessagesItem(e.getMessage());
    }
    return status;
  }

  @Scheduled(fixedRateString = "5", timeUnit = TimeUnit.MINUTES)
  @CacheEvict(allEntries = true, cacheNames = "providerStatus")
  public void resetProviderStatusCache() {
    log.debug("ProviderStatusCache reset");
  }

  @Scheduled(fixedRateString = "1", timeUnit = TimeUnit.MINUTES)
  @CacheEvict(allEntries = true, cacheNames = "samStatus")
  public void resetSamStatusCache() {
    log.debug("SamStatusCache reset");
  }

  private StatusApi createSamStatusApi() {
    var apiClientBuilder = new ApiClient().getHttpClient().newBuilder();
    var httpClient = apiClientBuilder.build();
    ApiClient samApiClient = new ApiClient();
    samApiClient.setHttpClient(httpClient);
    samApiClient.setBasePath(externalCredsConfig.getSamBasePath());
    return new StatusApi(samApiClient);
  }
}

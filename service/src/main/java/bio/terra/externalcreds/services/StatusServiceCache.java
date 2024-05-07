package bio.terra.externalcreds.services;

import bio.terra.externalcreds.dataAccess.SamStatusDAO;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.generated.model.SubsystemStatusDetail;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Caches for the status service so that status calls from k8s don't hammer the providers or Sam.
 */
@Component
@Slf4j
public class StatusServiceCache {
  private final ProviderOAuthClientCache providerOAuthClientCache;
  private final SamStatusDAO samStatusDAO;

  public StatusServiceCache(
      ProviderOAuthClientCache providerOAuthClientCache, SamStatusDAO samStatusDAO) {
    this.providerOAuthClientCache = providerOAuthClientCache;
    this.samStatusDAO = samStatusDAO;
  }

  // Cache the status of each provider.
  // This needs to happen so that if a providerClient fails to get created, we don't hammer the
  // provider with requests to our own status endpoint.
  public SubsystemStatusDetail getProviderStatus(Provider provider) {
    var status = new SubsystemStatusDetail();
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
  @Cacheable(cacheNames = "samStatus", unless = "#result.isOk() == false")
  public SubsystemStatusDetail getSamStatus() {
    var status = new SubsystemStatusDetail();
    status.name("sam");
    try {
      var samStatus = samStatusDAO.getSamStatus();
      status.ok(samStatus.getOk());
    } catch (Exception e) {
      log.warn("Error checking Sam status", e);
      status.ok(false);
      status.addMessagesItem(e.getMessage());
    }
    return status;
  }

  @Scheduled(fixedRateString = "5", timeUnit = TimeUnit.MINUTES)
  @CacheEvict(allEntries = true, cacheNames = "samStatus")
  public void resetSamStatusCache() {
    log.debug("SamStatusCache reset");
  }
}

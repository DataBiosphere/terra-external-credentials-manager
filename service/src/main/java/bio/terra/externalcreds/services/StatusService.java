package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.StatusDAO;
import bio.terra.externalcreds.generated.model.SubsystemStatus;
import bio.terra.externalcreds.generated.model.SystemStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StatusService {

  private final StatusDAO statusDAO;
  private final ExternalCredsConfig externalCredsConfig;
  private final StatusServiceCache providerStatusCache;

  public StatusService(
      StatusDAO statusDAO,
      ExternalCredsConfig externalCredsConfig,
      StatusServiceCache providerOAuthClientCache) {
    this.statusDAO = statusDAO;
    this.externalCredsConfig = externalCredsConfig;
    this.providerStatusCache = providerOAuthClientCache;
  }

  public SystemStatus getSystemStatus() {
    var currentStatus = new SystemStatus();

    var postgresStatus = getPostgresStatus();
    var samStatus = providerStatusCache.getSamStatus();
    var providerStatuses =
        externalCredsConfig.getProviders().keySet().stream()
            .map(providerStatusCache::getProviderStatus)
            .toList();

    // ECM is considered healthy if it can reach Sam and Postgres
    currentStatus.ok(postgresStatus.isOk() && samStatus.isOk());

    currentStatus.addSystemsItem(postgresStatus);
    currentStatus.addSystemsItem(samStatus);

    // ECM will report the statuses of each provider, but won't consider itself unhealthy if a
    // provider is down
    providerStatuses.forEach(currentStatus::addSystemsItem);

    return currentStatus;
  }

  private SubsystemStatus getPostgresStatus() {
    var status = new SubsystemStatus();
    status.name("postgres");
    try {
      status.ok(statusDAO.isPostgresOk());
    } catch (Exception e) {
      log.warn("Error checking database status", e);
      status.ok(false);
      status.addMessagesItem(e.getMessage());
    }
    return status;
  }
}

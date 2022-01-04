package bio.terra.externalcreds.services;

import bio.terra.externalcreds.dataAccess.StatusDAO;
import bio.terra.externalcreds.generated.model.SystemStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class StatusService {

  private final StatusDAO statusDAO;

  public StatusService(StatusDAO statusDAO) {
    this.statusDAO = statusDAO;
  }

  public SystemStatus getSystemStatus() {
    var currentStatus = new SystemStatus();

    try {
      currentStatus.putSystemsItem("postgres", statusDAO.isPostgresOk());
    } catch (Exception e) {
      log.warn("Error checking database status", e);
      currentStatus.putSystemsItem("postgres", false);
    }

    return currentStatus.ok(!currentStatus.getSystems().containsValue(false));
  }
}

package bio.terra.externalcreds.services;

import bio.terra.externalcreds.dataAccess.StatusDAO;
import bio.terra.externalcreds.generated.model.SubsystemStatus;
import bio.terra.externalcreds.generated.model.SystemStatus;
import org.springframework.stereotype.Service;

@Service
public class StatusService {

  private final StatusDAO statusDAO;

  public StatusService(StatusDAO statusDAO) {
    this.statusDAO = statusDAO;
  }

  public SystemStatus getSystemStatus() {
    SubsystemStatus subsystems = new SubsystemStatus();

    subsystems.put("postgres", statusDAO.isPostgresOk());

    return new SystemStatus().ok(!subsystems.containsValue(false)).systems(subsystems);
  }
}

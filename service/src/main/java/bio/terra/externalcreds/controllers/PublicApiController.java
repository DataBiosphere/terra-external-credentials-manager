package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.config.VersionProperties;
import bio.terra.externalcreds.generated.api.PublicApi;
import bio.terra.externalcreds.generated.model.SystemStatus;
import bio.terra.externalcreds.services.StatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class PublicApiController implements PublicApi {

  private final StatusService statusService;
  private final VersionProperties versionProperties;

  public PublicApiController(StatusService statusService, VersionProperties versionProperties) {
    this.statusService = statusService;
    this.versionProperties = versionProperties;
  }

  @Override
  public ResponseEntity<SystemStatus> getStatus() {
    SystemStatus currentStatus = statusService.getSystemStatus();

    return new ResponseEntity<>(
        currentStatus, currentStatus.isOk() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Override
  public ResponseEntity<VersionProperties> getVersion() {
    return ResponseEntity.ok(versionProperties);
  }
}

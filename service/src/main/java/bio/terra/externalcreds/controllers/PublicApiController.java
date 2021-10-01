package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.VersionProperties;
import bio.terra.externalcreds.generated.api.PublicApi;
import bio.terra.externalcreds.generated.model.SystemStatus;
import bio.terra.externalcreds.services.StatusService;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class PublicApiController implements PublicApi {

  private final StatusService statusService;
  private final ExternalCredsConfig externalCredsConfig;

  public PublicApiController(StatusService statusService, ExternalCredsConfig externalCredsConfig) {
    this.statusService = statusService;
    this.externalCredsConfig = externalCredsConfig;
  }

  // TODO: definitely test these both lol
  @Override
  public ResponseEntity<SystemStatus> getStatus() {
    var currentStatus = statusService.getSystemStatus();

    return ResponseEntity.status(
            currentStatus.isOk() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
        .body(currentStatus);
  }

  @Override
  public ResponseEntity<VersionProperties> getVersion() {
    return Optional.ofNullable(externalCredsConfig.getVersion())
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}

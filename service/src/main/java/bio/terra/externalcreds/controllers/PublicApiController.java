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
public record PublicApiController(
    StatusService statusService, ExternalCredsConfig externalCredsConfig) implements PublicApi {

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

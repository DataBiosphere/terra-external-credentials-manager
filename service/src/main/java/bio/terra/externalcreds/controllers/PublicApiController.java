package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.PublicApi;
import bio.terra.externalcreds.generated.model.SystemStatus;
import bio.terra.externalcreds.services.StatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicApiController implements PublicApi {

  private final StatusService statusService;

  public PublicApiController(StatusService statusService) {
    this.statusService = statusService;
  }

  @Override
  @GetMapping("/status")
  public ResponseEntity<SystemStatus> getStatus() {
    SystemStatus currentStatus = statusService.getSystemStatus();

    return new ResponseEntity<>(
        currentStatus, currentStatus.isOk() ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR);
  }
}

package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.PassportApi;
import bio.terra.externalcreds.generated.model.ValidatePassportRequest;
import bio.terra.externalcreds.generated.model.ValidatePassportResult;
import bio.terra.externalcreds.services.PassportService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record PassportApiController(PassportService passportService) implements PassportApi {

  @Override
  public ResponseEntity<ValidatePassportResult> validatePassport(ValidatePassportRequest body) {
    var result =
        passportService.validatePassport(
            body.getPassports(), OpenApiConverters.Input.convert(body.getCriteria()));
    return ResponseEntity.ok(OpenApiConverters.Output.convert(result));
  }
}

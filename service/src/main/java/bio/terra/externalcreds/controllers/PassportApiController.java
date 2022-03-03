package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.generated.api.PassportApi;
import bio.terra.externalcreds.generated.model.ValidatePassportRequest;
import bio.terra.externalcreds.generated.model.ValidateVisaResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class PassportApiController implements PassportApi {

  @Override
  public ResponseEntity<ValidateVisaResult> validatePassport(ValidatePassportRequest body) {
    System.out.println(body.toString());
    return null;
  }
}

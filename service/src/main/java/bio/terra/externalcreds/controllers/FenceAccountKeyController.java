package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.generated.api.FenceAccountKeyApi;
import bio.terra.externalcreds.generated.model.Provider;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record FenceAccountKeyController() implements FenceAccountKeyApi {

  @Override
  public ResponseEntity<String> getFenceAccountKey(Provider provider) {
    throw new NotFoundException("Fence service accounts no longer supported");
  }
}

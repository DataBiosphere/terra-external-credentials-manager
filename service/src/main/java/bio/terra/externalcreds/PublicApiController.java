package bio.terra.externalcreds;

import bio.terra.externalcreds.generated.api.PublicApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicApiController implements PublicApi {

  @Override
  @GetMapping("/status")
  public ResponseEntity<Void> getStatus() {
    return new ResponseEntity<>(HttpStatus.OK);
  }
}

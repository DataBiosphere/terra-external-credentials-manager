package bio.terra.externalcreds.controllers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Slf4j
public class SwaggerController {

  private String swaggerClientId;

  public SwaggerController() {
    try {
      try (var reader =
          new BufferedReader(
              new InputStreamReader(
                  new ClassPathResource("rendered/swagger-client-id").getInputStream()))) {
        swaggerClientId = reader.readLine();
      }
    } catch (IOException e) {
      log.error(
          "It doesn't look like configs have been rendered! Unable to parse swagger client id.", e);
      swaggerClientId = "";
    }
  }

  @GetMapping({"/", "/index.html", "swagger-ui.html"})
  public String getSwagger(Model model) {
    model.addAttribute("clientId", swaggerClientId);
    return "swagger-ui";
  }
}

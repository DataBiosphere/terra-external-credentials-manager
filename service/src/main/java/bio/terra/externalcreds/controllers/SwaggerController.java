package bio.terra.externalcreds.controllers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
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
      @Cleanup
      val swaggerClientIdStream =
          new ClassPathResource("rendered/swagger-client-id").getInputStream();
      try (val reader = new BufferedReader(new InputStreamReader(swaggerClientIdStream))) {
        swaggerClientId = reader.lines().collect(Collectors.joining("\n"));
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

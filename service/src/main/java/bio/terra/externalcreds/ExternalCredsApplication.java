package bio.terra.externalcreds;

import bio.terra.common.logging.LoggingInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication(scanBasePackages = {"bio.terra.externalcreds", "bio.terra.common.logging"})
public class ExternalCredsApplication {

  public static void main(String[] args) {
    new SpringApplicationBuilder(ExternalCredsApplication.class)
        .initializers(new LoggingInitializer())
        .run(args);
  }
}

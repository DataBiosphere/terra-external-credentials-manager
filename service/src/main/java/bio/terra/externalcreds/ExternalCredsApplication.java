package bio.terra.externalcreds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"bio.terra.externalcreds", "bio.terra.common.logging"})
public class ExternalCredsApplication {

  public static void main(String[] args) {
    SpringApplication.run(ExternalCredsApplication.class, args);
  }
}

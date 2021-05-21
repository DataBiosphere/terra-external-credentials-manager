package bio.terra.kong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"bio.terra.common.logging"})
public class KongApplication {

  public static void main(String[] args) {
    SpringApplication.run(KongApplication.class, args);
  }
}

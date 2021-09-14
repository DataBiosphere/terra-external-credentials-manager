package bio.terra.externalcreds;

import bio.terra.common.logging.LoggingInitializer;
import bio.terra.externalcreds.services.ProviderService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableScheduling
@ComponentScan(
    basePackages = {
      "bio.terra.externalcreds",
      "bio.terra.common.logging",
      "bio.terra.common.retry.transaction",
      "bio.terra.common.tracing"
    },
    excludeFilters = @Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class))
public class ExternalCredsCronApplication {

  public static void main(String[] args) {
    new SpringApplicationBuilder(ExternalCredsCronApplication.class)
        .initializers(new LoggingInitializer())
        .run(args);
  }

  private final ProviderService providerService;

  public ExternalCredsCronApplication(ProviderService providerService) {
    this.providerService = providerService;
  }

  @Scheduled(
      fixedRateString = "#{${externalcreds.refresh-background-job-interval-mins} * 60 * 1000}")
  public void checkForExpiringCredentials() {
    log.info("beginning checkForExpiringCredentials");
    var expiringPassportCount = providerService.refreshExpiringPassports();
    log.info(
        "completed checkForExpiringCredentials",
        Map.of("expiring_passport_count", expiringPassportCount));
  }

  @Scheduled(
      fixedRateString = "#{${externalcreds.validation-background-job-interval-mins} * 60 * 1000}")
  public void validateVisas() {
    log.info("beginning validateVisas");
    var checkedPassportCount = providerService.validatePassportsWithAccessTokenVisas();
    log.info("completed validateVisas", Map.of("checked_passport_count", checkedPassportCount));
  }
}

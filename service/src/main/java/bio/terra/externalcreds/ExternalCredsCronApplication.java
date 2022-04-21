package bio.terra.externalcreds;

import bio.terra.common.logging.LoggingInitializer;
import bio.terra.common.logging.LoggingUtils;
import bio.terra.externalcreds.services.ProviderService;
import bio.terra.externalcreds.services.SshKeyPairService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@SpringBootConfiguration
@EnableAutoConfiguration
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
  private final SshKeyPairService sshKeyPairService;

  public ExternalCredsCronApplication(
      ProviderService providerService, SshKeyPairService sshKeyPairService) {
    this.providerService = providerService;
    this.sshKeyPairService = sshKeyPairService;
  }

  @Scheduled(fixedRateString = "#{${externalcreds.background-job-interval-mins} * 60 * 1000}")
  public void checkForExpiringCredentials() {
    // check and refresh expiring visas and passports
    log.info("beginning checkForExpiringCredentials");
    var expiringPassportCount = providerService.refreshExpiringPassports();
    log.info(
        "completed checkForExpiringCredentials",
        Map.of("expiring_passport_count", expiringPassportCount));

    // check and validate visas not validated since job was last run
    log.info("beginning validateVisas");
    var checkedPassportCount = providerService.validateAccessTokenVisas();
    log.info("completed validateVisas", Map.of("checked_passport_count", checkedPassportCount));
  }

  @Scheduled(fixedRateString = "#{${externalcreds.background-job-interval-mins} * 60 * 1000}")
  public void checkForExpiringSshKeyPair() {
    log.info("Beginning checkForExpiringSshKeyPair");
    try {
      sshKeyPairService.reEncryptExpiringSshKeyPairs();
    } catch (Exception e) {
      LoggingUtils.logAlert(
          log, "Unexpected error during checkForExpiringSshKeyPair execution, see stacktrace below");
      log.error("checkForExpiringSshKeyPair failed, stacktrace: ", e);
    }
    log.info("Completed checkForExpiringSshKeyPair");
  }
}

package bio.terra.externalcreds;

import bio.terra.common.logging.LoggingInitializer;
import bio.terra.externalcreds.services.ProviderService;
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
      "bio.terra.common.retry.transaction"
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

  @Scheduled(fixedRate = 1000 * 60 * 10) // 10 minutes
  public void checkForExpiringCredentials() {
    // TODO: put real code here, this log message is just to verify that this is running
    log.info(providerService.getProviderList().toString());
  }
}

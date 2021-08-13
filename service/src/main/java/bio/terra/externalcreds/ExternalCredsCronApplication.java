package bio.terra.externalcreds;

import bio.terra.common.logging.LoggingInitializer;
import bio.terra.externalcreds.services.ProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Controller;

@Slf4j
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
    basePackages = {
      "bio.terra.externalcreds",
      "bio.terra.common.logging",
      "bio.terra.common.retry.transaction"
    },
    excludeFilters =
        @Filter(
            type = FilterType.ANNOTATION,
            classes = {Controller.class, SpringBootConfiguration.class}))
public class ExternalCredsCronApplication implements ApplicationRunner {

  public static void main(String[] args) {
    new SpringApplicationBuilder(ExternalCredsCronApplication.class)
        .initializers(new LoggingInitializer())
        .profiles("cron-app")
        .run(args);
  }

  private final ProviderService providerService;

  public ExternalCredsCronApplication(ProviderService providerService) {
    this.providerService = providerService;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    log.info(providerService.getProviderList().toString());
  }
}

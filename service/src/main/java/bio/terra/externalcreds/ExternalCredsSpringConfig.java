package bio.terra.externalcreds;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.models.StringToSshKeyPairTypeConverter;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Spring configuration class for loading application config and code defined beans. */
@Configuration
@EnableRetry
@EnableTransactionManagement
@EnableConfigurationProperties
@EnableCaching
@EnableScheduling
public class ExternalCredsSpringConfig implements WebMvcConfigurer {
  private final DataSource dataSource;

  public ExternalCredsSpringConfig(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  // This bean plus the @EnableTransactionManagement annotation above enables the use of the
  // @Transaction annotation to control the transaction properties of the data source.
  @Bean("transactionManager")
  public PlatformTransactionManager getTransactionManager() {
    return new JdbcTransactionManager(this.dataSource);
  }

  @Bean
  @ConfigurationProperties(value = "externalcreds", ignoreUnknownFields = false)
  public ExternalCredsConfig getExternalCredsSpringConfig() {
    return ExternalCredsConfig.create();
  }

  @Override
  public void addFormatters(FormatterRegistry registry) {
    registry.addConverter(new StringToSshKeyPairTypeConverter());
  }

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
  }
}

package bio.terra.externalcreds.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("externalcreds.providers")
public class ProviderConfig {
  /** The list of enabled provider services */
  private final Map<String, ProviderInfo> services;

  public ProviderConfig(Map<String, ProviderInfo> services) {
    this.services = services;
  }

  public Map<String, ProviderInfo> getServices() {
    return services;
  }
}

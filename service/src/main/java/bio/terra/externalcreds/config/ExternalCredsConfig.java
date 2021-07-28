package bio.terra.externalcreds.config;

import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("externalcreds")
public class ExternalCredsConfig {
  /** The list of enabled providers */
  private Map<String, ProviderProperties> providers;

  private Optional<VersionProperties> version = Optional.empty();

  public Map<String, ProviderProperties> getProviders() {
    return providers;
  }

  public Optional<VersionProperties> getVersion() {
    return version;
  }

  public void setVersion(VersionProperties version) {
    this.version = Optional.ofNullable(version);
  }

  public void setProviders(Map<String, ProviderProperties> providers) {
    this.providers = providers;
  }
}

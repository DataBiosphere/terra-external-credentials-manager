package bio.terra.externalcreds.config;

import java.time.Duration;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("externalcreds.providers")
@Getter
@Setter
public class ProviderConfig {
  /** The list of enabled provider services */
  private Map<String, ProviderInfo> services;

  @Getter
  @Setter
  public static class ProviderInfo {
    private String clientId;
    private String clientSecret;
    private Duration linkLifespan;
    private String issuer;
    private String revokeEndpoint;
    private Map<String, Object> additionalAuthorizationParameters;
  }
}

package bio.terra.externalcreds.config;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("externalcreds.providers")
@Data
public class ProviderConfig {
  /** The list of enabled provider services */
  private Map<String, ProviderInfo> services;

  @Data
  public static class ProviderInfo {
    private String clientId;
    private String clientSecret;
    private Duration linkLifespan;
    private String issuer;
    private String revokeEndpoint;
    private Map<String, Object> additionalAuthorizationParameters;

    // optional overrides for values in provider's /.well-known/openid-configuration
    private Optional<String> userInfoEndpoint = Optional.empty();
    private Optional<String> authorizationEndpoint = Optional.empty();
    private Optional<String> tokenEndpoint = Optional.empty();
    private Optional<String> jwksUri = Optional.empty();
  }
}

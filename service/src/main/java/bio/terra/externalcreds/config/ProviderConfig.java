package bio.terra.externalcreds.config;

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
    /** Length of validity for this provider */
    private String expiration;
    /** Revocation endpoint for this provider */
    private String revoke;

    private String clientId;
    private String clientSecret;
    private String openidConfiguration;
  }
}

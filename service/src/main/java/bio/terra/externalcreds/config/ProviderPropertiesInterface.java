package bio.terra.externalcreds.config;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface ProviderPropertiesInterface {
  String getClientId();

  String getClientSecret();

  Duration getLinkLifespan();

  String getIssuer();

  String getRevokeEndpoint();

  Map<String, Object> getAdditionalAuthorizationParameters();

  // optional overrides for values in provider's /.well-known/openid-configuration
  Optional<String> getUserInfoEndpoint();

  Optional<String> getAuthorizationEndpoint();

  Optional<String> getTokenEndpoint();

  Optional<String> getJwksUri();

  Optional<String> getValidationEndpoint();

  Collection<Pattern> getAllowedRedirectUriPatterns();
}

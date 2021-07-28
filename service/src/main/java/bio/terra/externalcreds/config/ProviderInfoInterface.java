package bio.terra.externalcreds.config;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Modifiable
@Value.Style(typeImmutable = "*", typeAbstract = "*Interface", typeModifiable = "*")
public interface ProviderInfoInterface {
  String getClientId();

  String getClientSecret();

  Duration getLinkLifespan();

  String getIssuer();

  String getRevokeEndpoint();

  Map<String, Object> getAdditionalAuthorizationParameters();

  // optional overrides for values in provider's /.well-known/openid-configuration
  default Optional<String> getUserInfoEndpoint() {
    return Optional.empty();
  }

  default Optional<String> getAuthorizationEndpoint() {
    return Optional.empty();
  }

  default Optional<String> getTokenEndpoint() {
    return Optional.empty();
  }

  default Optional<String> getJwksUri() {
    return Optional.empty();
  }
}

package bio.terra.externalcreds.config;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface ExternalCredsConfigInterface {
  Map<String, ProviderProperties> getProviders();

  // Nullable to make the generated class play nicely with spring: spring likes to call the getter
  // before the setter and without Nullable the immutables generated code errors because the field
  // is not set yet. Spring does not seem to recognize Optional.
  @Nullable
  VersionProperties getVersion();

  String getSamBasePath();

  int getBackgroundJobIntervalMins();

  Duration getTokenValidationDuration();

  Duration getVisaAndPassportRefreshDuration();

  /** List of URIs that are allowable in jku headers of JWTs */
  @Value.Default
  default Collection<URI> getAllowedJwksUris() {
    return List.of();
  }

  /** List of URIs that are allowable in iss claims of JWTs */
  @Value.Default
  default Collection<URI> getAllowedJwtIssuers() {
    return List.of();
  }

  boolean getAuthorizationChangeEventsEnabled();

  Optional<String> getAuthorizationChangeEventTopicName();

  @Value.Default
  default boolean getEnableKmsEncryption() {
    return false;
  }

  String getServiceGoogleProject();

  Optional<String> getKeyRingId();

  Optional<String> getKeyId();

  Optional<String> getKeyRingLocation();
}

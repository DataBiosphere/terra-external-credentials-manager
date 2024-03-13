package bio.terra.externalcreds.config;

import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.generated.model.Provider;
import jakarta.annotation.Nullable;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface ExternalCredsConfigInterface {

  EnumMap<Provider, ProviderProperties> getProviders();

  @Value.Derived
  default ProviderProperties getProviderProperties(Provider provider) {
    if (!getProviders().containsKey(provider)) {
      throw new NotFoundException("Provider not found: " + provider);
    }
    return getProviders().get(provider);
  }

  // Nullable to make the generated class play nicely with spring: spring likes to call the getter
  // before the setter and without Nullable the immutables generated code errors because the field
  // is not set yet. Spring does not seem to recognize Optional.
  @Nullable
  VersionProperties getVersion();

  String getSamBasePath();

  int getBackgroundJobIntervalMins();

  Duration getTokenValidationDuration();

  Duration getVisaAndPassportRefreshDuration();

  /** List of algorithms that are allowable in JWT headers */
  @Value.Default
  default Collection<String> getAllowedJwtAlgorithms() {
    return List.of();
  }

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

  @Nullable
  KmsConfiguration getKmsConfiguration();

  @Nullable
  BondDatastoreConfiguration getBondDatastoreConfiguration();

  @Value.Default
  default DistributedLockConfiguration getDistributedLockConfiguration() {
    return DistributedLockConfiguration.create().setLockTimeout(Duration.ofSeconds(30));
  }
  ;
}

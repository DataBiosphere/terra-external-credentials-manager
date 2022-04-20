package bio.terra.externalcreds.config;

import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface KmsConfigurationInterface {
  String getServiceGoogleProject();

  String getKeyRingId();

  String getKeyId();

  String getKeyRingLocation();
}

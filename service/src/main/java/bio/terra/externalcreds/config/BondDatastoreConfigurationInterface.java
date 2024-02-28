package bio.terra.externalcreds.config;

import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface BondDatastoreConfigurationInterface {
  String getDatastoreGoogleProject();

  String getDatastoreDatabaseId();
}

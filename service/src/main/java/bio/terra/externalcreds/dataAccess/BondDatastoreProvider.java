package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.spring.autoconfigure.datastore.DatastoreProvider;
import org.springframework.stereotype.Service;

@Service
public class BondDatastoreProvider implements DatastoreProvider {
  private final ExternalCredsConfig externalCredsConfig;

  public BondDatastoreProvider(ExternalCredsConfig externalCredsConfig) {
    this.externalCredsConfig = externalCredsConfig;
  }

  @Override
  public Datastore get() {
    var bondDatastoreConfig = externalCredsConfig.getBondDatastoreConfiguration();
    return DatastoreOptions.newBuilder()
        .setProjectId(bondDatastoreConfig.getDatastoreGoogleProject())
        .setDatabaseId(bondDatastoreConfig.getDatastoreDatabaseId())
        .build()
        .getService();
  }
}

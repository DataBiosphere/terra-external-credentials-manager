package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.BondDatastoreConfiguration;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class BondDatastoreProviderTest extends BaseTest {

  @Autowired private BondDatastoreProvider bondDatastoreProvider;

  @MockBean private ExternalCredsConfig externalCredsConfig;

  @Test
  void testGet() {
    var databaseId = "foobar";
    var project = "testProject";
    var bondDatastoreConfig =
        BondDatastoreConfiguration.create()
            .setDatastoreDatabaseId(databaseId)
            .setDatastoreGoogleProject(project);
    when(externalCredsConfig.getBondDatastoreConfiguration()).thenReturn(bondDatastoreConfig);

    var datastore = bondDatastoreProvider.get();
    assertEquals(databaseId, datastore.getOptions().getDatabaseId());
    assertEquals(project, datastore.getOptions().getProjectId());
  }
}

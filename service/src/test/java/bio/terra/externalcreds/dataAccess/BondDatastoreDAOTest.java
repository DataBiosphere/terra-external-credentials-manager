package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.BondRefreshTokenEntity;
import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class BondDatastoreDAOTest extends BaseTest {

  @MockBean private BondDatastoreProvider datastoreProvider;
  @Autowired private ExternalCredsConfig externalCredsConfig;
  @Autowired private BondDatastoreDAO bondDatastoreDAO;

  private Datastore bondDatastore;

  @BeforeEach
  public void setup() {
    bondDatastore = mock(Datastore.class);
    when(datastoreProvider.get()).thenReturn(bondDatastore);
    var keyFactory =
        new KeyFactory(
            externalCredsConfig.getBondDatastoreConfiguration().getDatastoreGoogleProject(),
            "",
            externalCredsConfig.getBondDatastoreConfiguration().getDatastoreDatabaseId());

    when(bondDatastore.newKeyFactory()).thenReturn(keyFactory);
  }
  
  @Test
  void testGetRefreshTokenDoesNotExist() {
    var userId = UUID.randomUUID().toString();
    var provider = Provider.FENCE;

    when(bondDatastore.get(any(Key.class))).thenReturn(null);

    var shouldBeEmpty = bondDatastoreDAO.getRefreshToken(userId, provider);

    assertEmpty(shouldBeEmpty);
  }

  @Test
  void testGFenceServiceAccountKey() {
    var userId = UUID.randomUUID().toString();
    var provider = Provider.FENCE;
    var expiresAt = Timestamp.now();

    when(bondDatastore.get(any(Key.class))).thenReturn(null);

    var actualEntity = bondDatastoreDAO.getFenceServiceAccountKey(userId, provider);

    assertTrue(actualEntity.isEmpty());
  }

  @Test
  void testGetServiceAccountKeyDoesNotExist() {
    var userId = UUID.randomUUID().toString();
    var provider = Provider.FENCE;

    when(bondDatastore.get(any(Key.class))).thenReturn(null);

    var shouldBeEmpty = bondDatastoreDAO.getFenceServiceAccountKey(userId, provider);

    assertEmpty(shouldBeEmpty);
  }

  @Test
  void testDeleteRefreshToken() {
    var userId = UUID.randomUUID().toString();
    var provider = Provider.FENCE;
    bondDatastoreDAO.deleteRefreshToken(userId, provider);

    verify(bondDatastore).delete(any(Key.class));
  }

  @Test
  void testDeleteFenceServiceAccountKey() {
    var userId = UUID.randomUUID().toString();
    var provider = Provider.FENCE;
    bondDatastoreDAO.deleteFenceServiceAccountKey(userId, provider);

    verify(bondDatastore).delete(any(Key.class));
  }
}

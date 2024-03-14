package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.BondFenceServiceAccountEntity;
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
  void getRefreshToken() {
    var userId = UUID.randomUUID().toString();
    var provider = Provider.FENCE;
    var issuedAt = Timestamp.now();
    var token = "TestToken";
    var userName = userId + "-name";

    when(bondDatastore.get(any(Key.class)))
        .thenAnswer(
            (Answer<Entity>)
                invocation -> {
                  var key = (Key) invocation.getArgument(0, Key.class);
                  return Entity.newBuilder(key)
                      .set(BondRefreshTokenEntity.issuedAtName, issuedAt)
                      .set(BondRefreshTokenEntity.tokenName, token)
                      .set(BondRefreshTokenEntity.userNameName, userName)
                      .build();
                });

    var actualEntity = bondDatastoreDAO.getRefreshToken(userId, provider);

    assertEquals(issuedAt.toDate().toInstant(), actualEntity.get().getIssuedAt());
    assertEquals(token, actualEntity.get().getToken());
    assertEquals(userName, actualEntity.get().getUsername());
  }

  @Test
  void getFenceServiceAccountKey() {
    var userId = UUID.randomUUID().toString();
    var provider = Provider.FENCE;
    var expiresAt = Timestamp.now();
    var keyJson = "TestKeyJson";
    var updateLockTimeout = "TestUpdateLockTimeout";

    when(bondDatastore.get(any(Key.class)))
        .thenAnswer(
            (Answer<Entity>)
                invocation -> {
                  var key = (Key) invocation.getArgument(0, Key.class);
                  return Entity.newBuilder(key)
                      .set(BondFenceServiceAccountEntity.expiresAtName, expiresAt)
                      .set(BondFenceServiceAccountEntity.keyJsonName, keyJson)
                      .set(BondFenceServiceAccountEntity.updateLockTimeoutName, updateLockTimeout)
                      .build();
                });

    var actualEntity = bondDatastoreDAO.getFenceServiceAccountKey(userId, provider);

    assertEquals(expiresAt.toDate().toInstant(), actualEntity.get().getExpiresAt());
    assertEquals(keyJson, actualEntity.get().getKeyJson());
    assertEquals(updateLockTimeout, actualEntity.get().getUpdateLockTimeout());
  }

  @Test
  void deleteRefreshToken() {
    var userId = UUID.randomUUID().toString();
    var provider = Provider.FENCE;
    bondDatastoreDAO.deleteRefreshToken(userId, provider);

    verify(bondDatastore).delete(any(Key.class));
  }

  @Test
  void deleteFenceServiceAccountKey() {
    var userId = UUID.randomUUID().toString();
    var provider = Provider.FENCE;
    bondDatastoreDAO.deleteFenceServiceAccountKey(userId, provider);

    verify(bondDatastore).delete(any(Key.class));
  }
}

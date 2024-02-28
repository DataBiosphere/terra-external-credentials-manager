package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.BondFenceServiceAccountEntity;
import bio.terra.externalcreds.models.BondRefreshTokenEntity;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.PathElement;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BondDatastoreDAO {

  private final Datastore datastore;

  private static final String FENCE_SERVICE_ACCOUNT_KIND = "FenceServiceAccount";
  private static final String REFRESH_TOKEN_KIND = "RefreshToken";

  public BondDatastoreDAO(ExternalCredsConfig externalCredsConfig) {
    var bondDatastoreConfig = externalCredsConfig.getBondDatastoreConfiguration();
    this.datastore =
        DatastoreOptions.newBuilder()
            .setProjectId(bondDatastoreConfig.getDatastoreGoogleProject())
            .setDatabaseId(bondDatastoreConfig.getDatastoreDatabaseId())
            .build()
            .getService();
  }

  public Optional<BondFenceServiceAccountEntity> getFenceServiceAccountKey(
      String userId, Provider provider) {
    var key =
        datastore
            .newKeyFactory()
            .setKind(FENCE_SERVICE_ACCOUNT_KIND)
            .addAncestor(PathElement.of("User", userId))
            .newKey(provider.toString());
    var entity = datastore.get(key);
    var bondFenceServiceAccountEntity =
        new BondFenceServiceAccountEntity.Builder()
            .key(key)
            .expiresAt(
                entity
                    .getTimestamp(BondFenceServiceAccountEntity.expiresAtName)
                    .toDate()
                    .toInstant())
            .keyJson(entity.getString(BondFenceServiceAccountEntity.keyJsonName))
            .updateLockTimeout(
                entity.getString(BondFenceServiceAccountEntity.updateLockTimeoutName));

    return Optional.ofNullable(bondFenceServiceAccountEntity.build());
  }

  public Optional<BondRefreshTokenEntity> getRefreshToken(String userId, Provider provider) {
    var key =
        datastore
            .newKeyFactory()
            .setKind(REFRESH_TOKEN_KIND)
            .addAncestor(PathElement.of("User", userId))
            .newKey(provider.toString());
    var entity = datastore.get(key);
    var bondRefreshTokenEntity =
        new BondRefreshTokenEntity.Builder()
            .key(key)
            .issuedAt(entity.getTimestamp(BondRefreshTokenEntity.issuedAtName).toDate().toInstant())
            .token(entity.getString(BondRefreshTokenEntity.tokenName))
            .username(entity.getString(BondRefreshTokenEntity.userNameName));
    return Optional.ofNullable(bondRefreshTokenEntity.build());
  }

  public void deleteRefreshToken(String userId, Provider provider) {
    var key =
        datastore
            .newKeyFactory()
            .setKind(REFRESH_TOKEN_KIND)
            .addAncestor(PathElement.of("User", userId))
            .newKey(provider.toString());
    datastore.delete(key);
  }

  public void deleteFenceServiceAccountKey(String userId, Provider provider) {
    var key =
        datastore
            .newKeyFactory()
            .setKind(FENCE_SERVICE_ACCOUNT_KIND)
            .addAncestor(PathElement.of("User", userId))
            .newKey(provider.toString());
    datastore.delete(key);
  }
}

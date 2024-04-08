package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.BondFenceServiceAccountEntity;
import bio.terra.externalcreds.models.BondRefreshTokenEntity;
import com.google.cloud.datastore.PathElement;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BondDatastoreDAO {

  private final BondDatastoreProvider bondDatastoreProvider;

  public static final String FENCE_SERVICE_ACCOUNT_KIND = "FenceServiceAccount";
  public static final String REFRESH_TOKEN_KIND = "RefreshToken";

  public BondDatastoreDAO(BondDatastoreProvider bondDatastoreProvider) {
    this.bondDatastoreProvider = bondDatastoreProvider;
  }

  public Optional<BondRefreshTokenEntity> getRefreshToken(String userId, Provider provider) {
    // note: returning a fake empty response from bond
    // since there seems to be an issue where ECM cannot get info from the datastore correctly

    //    var datastore = bondDatastoreProvider.get();
    //    var key =
    //        datastore
    //            .newKeyFactory()
    //            .setKind(REFRESH_TOKEN_KIND)
    //            .addAncestor(PathElement.of("User", userId))
    //            .newKey(provider.toString());
    //    var datastoreEntity = Optional.ofNullable(datastore.get(key));
    //    return datastoreEntity.map(
    //        entity ->
    //            new BondRefreshTokenEntity.Builder()
    //                .key(key)
    //                .issuedAt(
    //
    // entity.getTimestamp(BondRefreshTokenEntity.issuedAtName).toDate().toInstant())
    //                .token(entity.getString(BondRefreshTokenEntity.tokenName))
    //                .username(entity.getString(BondRefreshTokenEntity.userNameName))
    //                .build());
    return Optional.empty();
  }

  public Optional<BondFenceServiceAccountEntity> getFenceServiceAccountKey(
      String userId, Provider provider) {
    // note: returning a fake empty response from bond
    // since there seems to be an issue where ECM cannot get info from the datastore correctly

    //    var datastore = bondDatastoreProvider.get();
    //    var key =
    //        datastore
    //            .newKeyFactory()
    //            .setKind(FENCE_SERVICE_ACCOUNT_KIND)
    //            .addAncestor(PathElement.of("User", userId))
    //            .newKey(provider.toString());
    //    var datastoreEntity = Optional.ofNullable(datastore.get(key));
    //    return datastoreEntity.map(
    //        entity ->
    //            new BondFenceServiceAccountEntity.Builder()
    //                .key(key)
    //                .expiresAt(
    //                    entity
    //                        .getTimestamp(BondFenceServiceAccountEntity.expiresAtName)
    //                        .toDate()
    //                        .toInstant())
    //                .keyJson(entity.getString(BondFenceServiceAccountEntity.keyJsonName))
    //                .updateLockTimeout(
    //                    entity.getString(BondFenceServiceAccountEntity.updateLockTimeoutName))
    //                .build());
    return Optional.empty();
  }

  public void deleteRefreshToken(String userId, Provider provider) {
    var datastore = bondDatastoreProvider.get();
    var key =
        datastore
            .newKeyFactory()
            .setKind(REFRESH_TOKEN_KIND)
            .addAncestor(PathElement.of("User", userId))
            .newKey(provider.toString());
    datastore.delete(key);
  }

  public void deleteFenceServiceAccountKey(String userId, Provider provider) {
    var datastore = bondDatastoreProvider.get();
    var key =
        datastore
            .newKeyFactory()
            .setKind(FENCE_SERVICE_ACCOUNT_KIND)
            .addAncestor(PathElement.of("User", userId))
            .newKey(provider.toString());
    datastore.delete(key);
  }
}

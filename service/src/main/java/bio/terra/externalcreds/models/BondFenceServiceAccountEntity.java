package bio.terra.externalcreds.models;

import com.google.cloud.datastore.Key;
import jakarta.annotation.Nullable;
import java.time.Instant;
import org.immutables.value.Value;

@Value.Immutable
public interface BondFenceServiceAccountEntity extends WithBondFenceServiceAccountEntity {

  public static String expiresAtName = "expires_at";
  public static String keyJsonName = "key_json";
  public static String updateLockTimeoutName = "update_lock_timeout";

  Key getKey();

  Instant getExpiresAt();

  String getKeyJson();

  @Nullable
  String getUpdateLockTimeout();

  class Builder extends ImmutableBondFenceServiceAccountEntity.Builder {}
}

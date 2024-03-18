package bio.terra.externalcreds.models;

import com.google.cloud.datastore.Key;
import java.time.Instant;
import org.immutables.value.Value;

@Value.Immutable
public interface BondRefreshTokenEntity extends WithBondRefreshTokenEntity {

  public static String issuedAtName = "issued_at";
  public static String tokenName = "token";
  public static String userNameName = "username";

  Key getKey();

  Instant getIssuedAt();

  String getToken();

  String getUsername();

  class Builder extends ImmutableBondRefreshTokenEntity.Builder {}
}

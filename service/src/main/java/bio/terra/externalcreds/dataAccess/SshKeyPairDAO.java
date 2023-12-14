package bio.terra.externalcreds.dataAccess;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SshKeyPairDAO {
  private static final RowMapper<SshKeyPairInternal> SSH_KEY_PAIR_ROW_MAPPER =
      (rs, rowNum) ->
          new SshKeyPairInternal.Builder()
              .id(rs.getInt("id"))
              .userId(rs.getString("user_id"))
              .type(SshKeyPairType.valueOf(rs.getString("type")))
              .externalUserEmail(rs.getString("external_user_email"))
              .privateKey(rs.getBytes("private_key"))
              .publicKey(rs.getString("public_key"))
              .lastEncryptedTimestamp(
                  Optional.ofNullable(rs.getTimestamp("last_encrypted_timestamp"))
                      .map(timestamp -> timestamp.toInstant()))
              .build();

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ExternalCredsConfig externalCredsConfig;

  public SshKeyPairDAO(
      NamedParameterJdbcTemplate jdbcTemplate, ExternalCredsConfig externalCredsConfig) {
    this.jdbcTemplate = jdbcTemplate;
    this.externalCredsConfig = externalCredsConfig;
  }

  @ReadTransaction
  @WithSpan
  public Optional<SshKeyPairInternal> getSshKeyPair(String userId, SshKeyPairType type) {
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());
    var resourceSelectSql =
        "SELECT id, user_id, type, external_user_email, private_key, public_key, last_encrypted_timestamp"
            + " FROM ssh_key_pair WHERE user_id = :userId AND type = :type";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(resourceSelectSql, namedParameters, SSH_KEY_PAIR_ROW_MAPPER)));
  }

  @WriteTransaction
  @WithSpan
  public boolean deleteSshKeyPairIfExists(String userId, SshKeyPairType type) {
    var query = "DELETE FROM ssh_key_pair WHERE user_id = :userId and type = :type";
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  @WriteTransaction
  @WithSpan
  public SshKeyPairInternal upsertSshKeyPair(SshKeyPairInternal sshKeyPairInternal) {
    var query =
        "INSERT INTO ssh_key_pair (user_id, type, private_key, public_key, external_user_email, last_encrypted_timestamp)"
            + " VALUES (:userId, :type, :privateKey, :publicKey, :externalUserEmail,:lastEncryptedTimestamp)"
            + " ON CONFLICT (type, user_id) DO UPDATE SET"
            + " private_key = excluded.private_key,"
            + " public_key = excluded.public_key,"
            + " external_user_email = excluded.external_user_email,"
            + " last_encrypted_timestamp = excluded.last_encrypted_timestamp"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", sshKeyPairInternal.getUserId())
            .addValue("type", sshKeyPairInternal.getType().name())
            .addValue("privateKey", sshKeyPairInternal.getPrivateKey())
            .addValue("publicKey", sshKeyPairInternal.getPublicKey())
            .addValue("externalUserEmail", sshKeyPairInternal.getExternalUserEmail())
            .addValue(
                "lastEncryptedTimestamp",
                sshKeyPairInternal
                    .getLastEncryptedTimestamp()
                    .map(instant -> Timestamp.from(instant))
                    .orElse(null));

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return sshKeyPairInternal.withId(
        Objects.requireNonNull(generatedKeyHolder.getKey()).intValue());
  }

  /** Gets expired or un-encrypted ssh key pair. */
  public List<SshKeyPairInternal> getExpiredOrUnEncryptedSshKeyPair(
      Instant refreshCutoffTimestamp) {
    var kmsConfig = externalCredsConfig.getKmsConfiguration();
    if (kmsConfig == null) {
      return Collections.emptyList();
    }
    var namedParameters =
        new MapSqlParameterSource("refreshCutoffTimestamp", Timestamp.from(refreshCutoffTimestamp));
    var query =
        "SELECT DISTINCT id, user_id, type, external_user_email, private_key, public_key, last_encrypted_timestamp"
            + " FROM ssh_key_pair"
            + " WHERE last_encrypted_timestamp <= :refreshCutoffTimestamp"
            + " or last_encrypted_timestamp IS NULL";

    return jdbcTemplate.query(query, namedParameters, SSH_KEY_PAIR_ROW_MAPPER);
  }
}

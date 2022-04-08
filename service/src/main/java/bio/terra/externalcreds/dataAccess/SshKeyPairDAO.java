package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.SshKeyPair;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.xml.crypto.Data;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import scala.Predef.StringFormat;

@Repository
public class SshKeyPairDAO {
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ExternalCredsConfig externalCredsConfig;
  private final SshKeyPairRowMapper sshKeyPairRowMapper;
  private final KmsEncryptDecryptHelper kmsEncryptDecryptHelper;

  public SshKeyPairDAO(
      NamedParameterJdbcTemplate jdbcTemplate,
      ExternalCredsConfig externalCredsConfig,
      KmsEncryptDecryptHelper kmsEncryptDecryptHelper) {
    this.jdbcTemplate = jdbcTemplate;
    this.externalCredsConfig = externalCredsConfig;
    sshKeyPairRowMapper = new SshKeyPairRowMapper(externalCredsConfig);
    this.kmsEncryptDecryptHelper = kmsEncryptDecryptHelper;
  }

  public Optional<SshKeyPairInternal> getSshKeyPair(String userId, SshKeyPairType type) {
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());
    var resourceSelectSql =
        "SELECT id, user_id, type, external_user_email, private_key, public_key"
            + " FROM ssh_key_pair WHERE user_id = :userId AND type = :type";
    var sshKeyPair = Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(resourceSelectSql, namedParameters, sshKeyPairRowMapper)));
    // Maybe re-encrypt the private key if the encryption timestamp is old (i.e. the key is using
    // an older version key) or the key is not encrypted.
    if (externalCredsConfig.getKmsConfiguration().isPresent()) {
      var timestampSql =
          "SELECT key_pair_last_modified FROM ssh_key_pair WHERE user_id = :userId AND type = :type";
      var timestampOptional =
          Optional.ofNullable(
              DataAccessUtils.singleResult(
                  jdbcTemplate.query(timestampSql, namedParameters,
                      (rs, r) -> rs.getTimestamp("key_pair_last_encrypted"))));
      if (timestampOptional.isEmpty() ||
          Duration.between(timestampOptional.get().toInstant(), Instant.now()).compareTo(
          externalCredsConfig.getKmsConfiguration().get().getKeyRotationIntervalDays()) >= 0) {
        // re-encrypt the ssh private key with a newer key version.
        sshKeyPair.ifPresent(this::upsertSshKeyPair);
      }
    }
    return sshKeyPair;
  }

  public boolean deleteSshKeyPairIfExists(String userId, SshKeyPairType type) {
    var query = "DELETE FROM ssh_key_pair WHERE user_id = :userId and type = :type";
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  public SshKeyPairInternal upsertSshKeyPair(SshKeyPairInternal sshKeyPairInternal) {
    var query =
        "INSERT INTO ssh_key_pair (user_id, type, private_key, public_key, external_user_email, key_pair_last_encrypted)"
            + " VALUES (:userId, :type, :privateKey, :publicKey, :externalUserEmail, :currentTimestamp )"
            + " ON CONFLICT (type, user_id) DO UPDATE SET"
            + " private_key = excluded.private_key,"
            + " public_key = excluded.public_key,"
            + " external_user_email = excluded.external_user_email"
            + " key_pair_last_encrypted = excluded.key_pair_last_encrypted"
            + " RETURNING id";

    var sshPrivateKey = sshKeyPairInternal.getPrivateKey();
    var namedParameters = new MapSqlParameterSource();
    if (externalCredsConfig.getKmsConfiguration().isPresent()) {
      // Record the timestamp when the key is encrypted.
      namedParameters.addValue("currentTimestamp", Timestamp.from(Instant.now()));
      sshPrivateKey = kmsEncryptDecryptHelper.encryptSymmetric(sshPrivateKey);
    }
    namedParameters
            .addValue("userId", sshKeyPairInternal.getUserId())
            .addValue("type", sshKeyPairInternal.getType().name())
            .addValue("privateKey", sshPrivateKey)
            .addValue("publicKey", sshKeyPairInternal.getPublicKey())
            .addValue("externalUserEmail", sshKeyPairInternal.getExternalUserEmail());

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return sshKeyPairInternal.withId(
        Objects.requireNonNull(generatedKeyHolder.getKey()).intValue());
  }

  public List<SshKeyPairInternal> getSshKeyPairWithExpiredOrNullEncryptionTimeStamp() {
    if (externalCredsConfig.getKmsConfiguration().isEmpty()) {
      return Collections.EMPTY_LIST;
    }
    var query =
        "SELECT id, user_id, type, external_user_email, private_key, public_key"
            + " FROM ssh_key_pair"
            + " WHERE key_pair_last_encrypted <= CURRENT_DATE - INTERVAL ':keyRotationDays DAYS'"
            + " or key_pair_last_encrypted IS NULL";
    var namedParameters = new MapSqlParameterSource()
        .addValue("keyRotationDays",
            (int) externalCredsConfig.getKmsConfiguration().get().getKeyRotationIntervalDays().toDays());
    return jdbcTemplate.query(query, namedParameters, sshKeyPairRowMapper);
  }

  private class SshKeyPairRowMapper implements RowMapper<SshKeyPairInternal> {

    private final ExternalCredsConfig externalCredsConfig;

    SshKeyPairRowMapper(ExternalCredsConfig externalCredsConfig) {
      this.externalCredsConfig = externalCredsConfig;
    }

    @Override
    public SshKeyPairInternal mapRow(ResultSet rs, int rowNum) throws SQLException {
      String privateKey = rs.getString("private_key");
      if (externalCredsConfig.getKmsConfiguration().isPresent()) {
        privateKey = kmsEncryptDecryptHelper.decryptSymmetric(privateKey);
      }
      return new SshKeyPairInternal.Builder()
          .id(rs.getInt("id"))
          .userId(rs.getString("user_id"))
          .type(SshKeyPairType.valueOf(rs.getString("type")))
          .externalUserEmail(rs.getString("external_user_email"))
          .privateKey(privateKey)
          .publicKey(rs.getString("public_key"))
          .build();
    }
  }
}

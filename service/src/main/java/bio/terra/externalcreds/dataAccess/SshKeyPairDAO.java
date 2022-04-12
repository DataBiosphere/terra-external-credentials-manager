package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
        "SELECT id, user_id, type, external_user_email, private_key, public_key, expires"
            + " FROM ssh_key_pair WHERE user_id = :userId AND type = :type";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(resourceSelectSql, namedParameters, sshKeyPairRowMapper)));
  }

  public boolean deleteSshKeyPairIfExists(String userId, SshKeyPairType type) {
    var query = "DELETE FROM ssh_key_pair WHERE user_id = :userId and type = :type";
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  public SshKeyPairInternal upsertSshKeyPair(SshKeyPairInternal sshKeyPairInternal) {
    var query =
        "INSERT INTO ssh_key_pair (user_id, type, private_key, public_key, external_user_email, expires)"
            + " VALUES (:userId, :type, :privateKey, :publicKey, :externalUserEmail,"
            + (externalCredsConfig.getKmsConfiguration().isPresent() ? " :expires)" : " NULL)")
            + " ON CONFLICT (type, user_id) DO UPDATE SET"
            + " private_key = excluded.private_key,"
            + " public_key = excluded.public_key,"
            + " external_user_email = excluded.external_user_email,"
            + " expires = excluded.expires"
            + " RETURNING id";

    var sshPrivateKey = sshKeyPairInternal.getPrivateKey();
    var namedParameters = new MapSqlParameterSource();
    var kmsConfiguration = externalCredsConfig.getKmsConfiguration();
    if (kmsConfiguration.isPresent()) {
      // Record the timestamp when the key is encrypted.
      namedParameters.addValue(
          "expires",
          Timestamp.from(
              Instant.now().plus(kmsConfiguration.get().getSshKeyPairRefreshDuration())));
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

  /**
   * Gets expired or un-encrypted ssh key pair.
   *
   * @param expirationCutoff timestamp by which the key encryption is expired and should be
   *     re-encrypted.
   */
  public List<SshKeyPairInternal> getExpiredOrUnEncryptedSshKeyPair(Timestamp expirationCutoff) {
    var namedParameters = new MapSqlParameterSource("expirationCutoff", expirationCutoff);
    var query =
        "SELECT DISTINCT id, user_id, type, external_user_email, private_key, public_key, expires"
            + " FROM ssh_key_pair"
            + " WHERE expires <= :expirationCutoff or expires IS NULL";

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
      if (externalCredsConfig.getKmsConfiguration().isPresent()
          && rs.getTimestamp("expires") != null) {
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

package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPairInternal;
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
              .privateKey(rs.getString("private_key"))
              .publicKey(rs.getString("public_key"))
              .build();

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public SshKeyPairDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<SshKeyPairInternal> getSshKeyPair(String userId, SshKeyPairType type) {
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());
    var resourceSelectSql =
        "SELECT id, user_id, type, external_user_email, private_key, public_key"
            + " FROM ssh_key_pair WHERE user_id = :userId AND type = :type";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(resourceSelectSql, namedParameters, SSH_KEY_PAIR_ROW_MAPPER)));
  }

  public boolean deleteSshKeyPairIfExists(String userId, SshKeyPairType type) {
    var query = "DELETE FROM ssh_key_pair WHERE user_id = :userId and type = :type";
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  public SshKeyPairInternal upsertSshKeyPair(SshKeyPairInternal sshKeyPairInternal) {
    var query =
        "INSERT INTO ssh_key_pair (user_id, type, private_key, public_key, external_user_email)"
            + " VALUES (:userId, :type, :privateKey, :publicKey, :externalUserEmail)"
            + " ON CONFLICT (type, user_id) DO UPDATE SET"
            + " private_key = excluded.private_key,"
            + " public_key = excluded.public_key,"
            + " external_user_email = excluded.external_user_email"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", sshKeyPairInternal.getUserId())
            .addValue("type", sshKeyPairInternal.getType().name())
            .addValue("privateKey", sshKeyPairInternal.getPrivateKey())
            .addValue("publicKey", sshKeyPairInternal.getPublicKey())
            .addValue("externalUserEmail", sshKeyPairInternal.getExternalUserEmail());

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return sshKeyPairInternal.withId(
        Objects.requireNonNull(generatedKeyHolder.getKey()).intValue());
  }
}

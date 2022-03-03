package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.generated.model.SshKeyPairType;
import bio.terra.externalcreds.models.SshKeyPair;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SshKeyDAO {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  public SshKeyDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<SshKeyPair> getSecret(String userId, SshKeyPairType type) {
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());
    var query = "SELECT * FROM ssh_key WHERE user_id = :userId AND type = :type";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, new SshKeyPairRowMapper())));
  }

  public boolean deleteSecret(String userId, SshKeyPairType type) {
    var query = "DELETE FROM ssh_key WHERE user_id = :userId and type = :type";
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  public SshKeyPair upsertSshKey(SshKeyPair sshKey) {
    var query =
        "INSERT INTO ssh_key (user_id, type, private_key, public_key, external_user_email)"
            + " VALUES (:userId, :type, :privateKey, :publicKey, :externalUserEmail)"
            + " ON CONFLICT (type, user_id) DO UPDATE SET"
            + " private_key = excluded.private_key,"
            + " public_key = excluded.public_key,"
            + " external_user_email = excluded.external_user_email"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", sshKey.getUserId())
            .addValue("type", sshKey.getType().name())
            .addValue("privateKey", sshKey.getPrivateKey())
            .addValue("publicKey", sshKey.getPublicKey())
            .addValue("externalUserEmail", sshKey.getExternalUserEmail());

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return sshKey.withId(Objects.requireNonNull(generatedKeyHolder.getKey()).intValue());
  }

  private static class SshKeyPairRowMapper implements RowMapper<SshKeyPair> {

    @Override
    public SshKeyPair mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new SshKeyPair.Builder()
          .id(rs.getInt("id"))
          .userId(rs.getString("user_id"))
          .type(SshKeyPairType.valueOf(rs.getString("type")))
          .externalUserEmail(rs.getString("external_user_email"))
          .privateKey(rs.getString("private_key"))
          .publicKey(rs.getString("public_key"))
          .build();
    }
  }
}

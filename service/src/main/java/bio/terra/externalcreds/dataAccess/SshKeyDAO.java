package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.generated.model.SshKeyType;
import bio.terra.externalcreds.models.SshKey;
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

  public Optional<SshKey> getSecret(String userId, SshKeyType type) {
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());
    var query = "SELECT * FROM ssh_key WHERE user_id = :userId AND type = :type";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, new SshKeyRowMapper())));
  }

  public boolean deleteSecret(String userId, SshKeyType type) {
    var query = "DELETE FROM ssh_key WHERE user_id = :userId and type = :type";
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type);

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  public SshKey upsertSshKey(SshKey sshKey) {
    var query =
        "INSERT INTO ssh_key (user_id, id, type, key, external_user_name, external_user_email)"
            + " VALUES (:userId, :secretId, :name, :description, :type, :secret_content, cast(:attributes AS jsonb)"
            + " ON CONFLICT (type, user_id) DO UPDATE SET"
            + " id = excluded.id,"
            + " key = excluded.key,"
            + " external_user_email = excluded.external_user_email"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", sshKey.getUserId())
            .addValue("id", sshKey.getId())
            .addValue("type", sshKey.getType().name())
            .addValue("key", sshKey.getPrivateKey())
            .addValue("externalUserEmail", sshKey.getExternalUserEmail().orElse(""));

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters);

    return sshKey.withId(Objects.requireNonNull(generatedKeyHolder.getKey()).intValue());
  }

  private static class SshKeyRowMapper implements RowMapper<SshKey> {

    @Override
    public SshKey mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new SshKey.Builder()
          .id(rs.getInt("id"))
          .userId(rs.getString("user_id"))
          .type(SshKeyType.valueOf(rs.getString("type")))
          .externalUserEmail(rs.getString("external_user_email"))
          .privateKey(rs.getString("key"))
          .build();
    }
  }
}

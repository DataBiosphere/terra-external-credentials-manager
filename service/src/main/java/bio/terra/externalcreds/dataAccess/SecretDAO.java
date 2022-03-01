package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.Secret;
import bio.terra.externalcreds.models.SecretType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class SecretDAO {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  public SecretDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<Secret> getSecret(String userId, SecretType type) {
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type.name());
    var query = "SELECT * FROM secret WHERE user_id = :userId AND type = :type";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, new SecretRowMapper())));
  }

  public boolean deleteSecret(String userId, SecretType type) {
    var query = "DELETE FROM secret WHERE user_id = :userId and type = :type";
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("type", type);

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  public Secret insertSecret(Secret secret) {
    var query =
        "INSERT INTO secret (user_id, secret_id, name, description, type, secret_content, attributes)"
            + " VALUES (:userId, :secretId, :name, :description, :type, :secret_content, cast(:attributes AS jsonb)";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", secret.getUserId())
            .addValue("secretId", secret.getId())
            .addValue("name", secret.getName())
            .addValue("description", secret.getDescription().orElse(""))
            .addValue("type", secret.getType().name())
            .addValue("secret_content", secret.getSecretContent())
            .addValue("attributes", secret.getAttributes().orElse(""));

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return secret;
  }

  private static class SecretRowMapper implements RowMapper<Secret> {

    @Override
    public Secret mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new Secret.Builder()
          .id(rs.getInt("id"))
          .userId(rs.getString("user_id"))
          .type(SecretType.valueOf(rs.getString("type")))
          .name(rs.getString("name"))
          .description(rs.getString("description"))
          .attributes(rs.getString("attributes"))
          .secretContent(rs.getString("secret_content"))
          .build();
    }
  }
}

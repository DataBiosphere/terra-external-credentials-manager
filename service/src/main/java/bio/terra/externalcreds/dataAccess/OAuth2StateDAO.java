package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.OAuth2State;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class OAuth2StateDAO {
  private final NamedParameterJdbcTemplate jdbcTemplate;

  public OAuth2StateDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WithSpan
  public OAuth2State upsertOidcState(String userId, OAuth2State oAuth2State) {
    var query =
        "INSERT INTO oauth2_state (user_id, provider_name, random)"
            + " VALUES (:userId, :providerName, :random)"
            + " ON CONFLICT (user_id, provider_name) DO UPDATE SET"
            + " random = excluded.random";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("providerName", oAuth2State.getProvider())
            .addValue("random", oAuth2State.getRandom());

    jdbcTemplate.update(query, namedParameters);

    return oAuth2State;
  }

  @WithSpan
  public boolean deleteOidcStateIfExists(String userId, OAuth2State oAuth2State) {
    var query =
        "DELETE FROM oauth2_state WHERE user_id = :userId and provider_name = :providerName and random = :random";
    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("providerName", oAuth2State.getProvider())
            .addValue("random", oAuth2State.getRandom());

    return jdbcTemplate.update(query, namedParameters) > 0;
  }
}

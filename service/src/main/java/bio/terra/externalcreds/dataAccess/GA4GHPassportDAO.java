package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.GA4GHPassport;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class GA4GHPassportDAO {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public GA4GHPassportDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Deletes the passport for the given linked account.
   *
   * @param linkedAccountId id of the linked account
   * @return true if a passport was deleted
   */
  @WithSpan
  public boolean deletePassport(int linkedAccountId) {
    var namedParameters = new MapSqlParameterSource("linkedAccountId", linkedAccountId);
    var query = "DELETE FROM ga4gh_passport WHERE linked_account_id = :linkedAccountId";
    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  @WithSpan
  public GA4GHPassport insertPassport(GA4GHPassport passport) {
    var query =
        "INSERT INTO ga4gh_passport (linked_account_id, jwt, expires, jwt_id)"
            + " VALUES (:linkedAccountId, :jwt, :expires, :jwtId)"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("linkedAccountId", passport.getLinkedAccountId().orElseThrow())
            .addValue("jwt", passport.getJwt())
            .addValue("expires", passport.getExpires())
            .addValue("jwtId", passport.getJwtId());

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return passport.withId(Objects.requireNonNull(generatedKeyHolder.getKey()).intValue());
  }

  @WithSpan
  public Optional<GA4GHPassport> getPassport(String userId, Provider provider) {
    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("provider", provider.name());
    var query =
        "SELECT p.* FROM ga4gh_passport p"
            + " INNER JOIN linked_account la ON la.id = p.linked_account_id"
            + " WHERE la.user_id = :userId"
            + " AND la.provider = :provider::provider_enum";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, new GA4GHPassportRowMapper())));
  }

  private static class GA4GHPassportRowMapper implements RowMapper<GA4GHPassport> {

    @Override
    public GA4GHPassport mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new GA4GHPassport.Builder()
          .id(rs.getInt("id"))
          .linkedAccountId(rs.getInt("linked_account_id"))
          .jwt(rs.getString("jwt"))
          .expires(rs.getTimestamp("expires"))
          .jwtId(rs.getString("jwt_id"))
          .build();
    }
  }
}

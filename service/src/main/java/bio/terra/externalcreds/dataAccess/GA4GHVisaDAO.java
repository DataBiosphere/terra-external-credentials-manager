package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.GA4GHVisa;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class GA4GHVisaDAO {

  final NamedParameterJdbcTemplate jdbcTemplate;

  public GA4GHVisaDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public GA4GHVisa insertVisa(GA4GHVisa visa) {
    val query =
        "INSERT INTO ga4gh_visa (passport_id, visa_type, jwt, expires, issuer, token_type, last_validated)"
            + " VALUES (:passportId, :visaType, :jwt, :expires, :issuer, :tokenType, :lastValidated)"
            + " RETURNING id";

    val namedParameters =
        new MapSqlParameterSource()
            .addValue("passportId", visa.getPassportId())
            .addValue("visaType", visa.getVisaType())
            .addValue("jwt", visa.getJwt())
            .addValue("expires", visa.getExpires())
            .addValue("issuer", visa.getIssuer())
            .addValue("tokenType", visa.getTokenType())
            .addValue("lastValidated", visa.getLastValidated());

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    val generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return visa.withId(generatedKeyHolder.getKey().intValue());
  }

  /**
   * Deletes all visas belonging to the given passport.
   *
   * @param passportId id of the passport
   * @return the number of visas deleted
   */
  public int deleteVisas(int passportId) {
    val namedParameters = new MapSqlParameterSource().addValue("passportId", passportId);
    val query = "DELETE FROM ga4gh_visa v WHERE v.passport_id = :passportId";
    return jdbcTemplate.update(query, namedParameters);
  }
}

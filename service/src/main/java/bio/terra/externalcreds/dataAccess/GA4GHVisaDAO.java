package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
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
    var query =
        "INSERT INTO ga4gh_visa (passport_id, visa_type, jwt, expires, issuer, token_type, last_validated)"
            + " VALUES (:passportId, :visaType, :jwt, :expires, :issuer, :tokenType, :lastValidated)"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("passportId", visa.getPassportId().orElseThrow())
            .addValue("visaType", visa.getVisaType())
            .addValue("jwt", visa.getJwt())
            .addValue("expires", visa.getExpires())
            .addValue("issuer", visa.getIssuer())
            .addValue(
                "tokenType",
                visa.getTokenType(),
                Types.OTHER) // because it's an enum, not a string...
            .addValue("lastValidated", visa.getLastValidated().orElse(null));

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return visa.withId(generatedKeyHolder.getKey().intValue());
  }

  public List<GA4GHVisa> listVisas(int passportId) {
    var namedParameters = new MapSqlParameterSource("passportId", passportId);
    var query = "SELECT * FROM ga4gh_visa WHERE passport_id = :passportId";
    return jdbcTemplate.query(query, namedParameters, new GA4GHVisaRowMapper());
  }

  private static class GA4GHVisaRowMapper implements RowMapper<GA4GHVisa> {

    @Override
    public GA4GHVisa mapRow(ResultSet rs, int rowNum) throws SQLException {
      return GA4GHVisa.builder()
          .id(rs.getInt("id"))
          .passportId(rs.getInt("passport_id"))
          .jwt(rs.getString("jwt"))
          .expires(rs.getTimestamp("expires"))
          .issuer(rs.getString("issuer"))
          .tokenType(TokenTypeEnum.valueOf(rs.getString("token_type")))
          .lastValidated(Optional.ofNullable(rs.getTimestamp("last_validated")))
          .visaType(rs.getString("visa_type"))
          .build();
    }
  }
}

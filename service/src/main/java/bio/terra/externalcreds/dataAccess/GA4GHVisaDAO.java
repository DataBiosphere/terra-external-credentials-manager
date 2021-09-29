package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.TokenTypeEnum;
import bio.terra.externalcreds.models.VisaVerificationDetails;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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

  public List<GA4GHVisa> listVisas(String userId, String providerId) {
    var namedParameters =
        new MapSqlParameterSource("userId", userId).addValue("providerId", providerId);
    var query =
        "SELECT v.* FROM ga4gh_visa v"
            + " INNER JOIN ga4gh_passport p ON p.id = v.passport_id"
            + " INNER JOIN linked_account la ON la.id = p.linked_account_id"
            + " WHERE la.user_id = :userId"
            + " AND la.provider_id = :providerId";
    return jdbcTemplate.query(query, namedParameters, new GA4GHVisaRowMapper());
  }

  public List<VisaVerificationDetails> getUnvalidatedAccessTokenVisaDetails(
      Timestamp validationCutoff) {
    var namedParameters = new MapSqlParameterSource("validationCutoff", validationCutoff);

    var query =
        "SELECT DISTINCT la.id as linked_account_id, la.provider_id, v.jwt FROM linked_account la"
            + " JOIN ga4gh_passport p"
            + " ON p.linked_account_id = la.id"
            + " JOIN ga4gh_visa v"
            + " ON v.passport_id = p.id"
            + " WHERE v.token_type = "
            + String.format("'%s'", TokenTypeEnum.access_token)
            + " AND v.last_validated <= :validationCutoff";

    return jdbcTemplate.query(query, namedParameters, new VisaVerificationDetailsRowMapper());
  }

  private static class GA4GHVisaRowMapper implements RowMapper<GA4GHVisa> {

    @Override
    public GA4GHVisa mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new GA4GHVisa.Builder()
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

  private static class VisaVerificationDetailsRowMapper
      implements RowMapper<VisaVerificationDetails> {

    @Override
    public VisaVerificationDetails mapRow(ResultSet rs, int rowNum) throws SQLException {
      return new VisaVerificationDetails.Builder()
          .linkedAccountId(rs.getInt("linked_account_id"))
          .providerId(rs.getString("provider_id"))
          .visaJwt(rs.getString("jwt"))
          .build();
    }
  }
}

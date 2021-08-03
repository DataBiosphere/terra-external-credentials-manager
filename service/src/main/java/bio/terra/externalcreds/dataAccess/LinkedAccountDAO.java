package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.ImmutableLinkedAccount;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
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
public class LinkedAccountDAO {

  final NamedParameterJdbcTemplate jdbcTemplate;

  public LinkedAccountDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<LinkedAccount> getLinkedAccount(String userId, String providerId) {
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("providerId", providerId);
    var query =
        "SELECT * FROM linked_account WHERE user_id = :userId and provider_id = :providerId";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, new LinkedAccountRowMapper())));
  }

  public LinkedAccount upsertLinkedAccount(LinkedAccount linkedAccount) {
    var query =
        "INSERT INTO linked_account (user_id, provider_id, refresh_token, expires, external_user_id)"
            + " VALUES (:userId, :providerId, :refreshToken, :expires, :externalUserId)"
            + " ON CONFLICT (user_id, provider_id) DO UPDATE SET"
            + " refresh_token = excluded.refresh_token,"
            + " expires = excluded.expires,"
            + " external_user_id = excluded.external_user_id"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", linkedAccount.getUserId())
            .addValue("providerId", linkedAccount.getProviderId())
            .addValue("refreshToken", linkedAccount.getRefreshToken())
            .addValue("expires", linkedAccount.getExpires())
            .addValue("externalUserId", linkedAccount.getExternalUserId());

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return ImmutableLinkedAccount.copyOf(linkedAccount)
        .withId(generatedKeyHolder.getKey().intValue());
  }

  /**
   * @param userId
   * @param providerId
   * @return boolean whether or not an account was found and deleted
   */
  public boolean deleteLinkedAccountIfExists(String userId, String providerId) {
    var query = "DELETE FROM linked_account WHERE user_id = :userId and provider_id = :providerId";
    var namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("providerId", providerId);

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  private static class LinkedAccountRowMapper implements RowMapper<LinkedAccount> {

    @Override
    public LinkedAccount mapRow(ResultSet rs, int rowNum) throws SQLException {
      return ImmutableLinkedAccount.builder()
          .id(rs.getInt("id"))
          .userId(rs.getString("user_id"))
          .providerId(rs.getString("provider_id"))
          .refreshToken(rs.getString("refresh_token"))
          .expires(rs.getTimestamp("expires"))
          .externalUserId(rs.getString("external_user_id"))
          .build();
    }
  }
}

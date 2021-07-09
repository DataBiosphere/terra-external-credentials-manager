package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
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

  public LinkedAccount getLinkedAccount(String userId, String providerId) {
    val namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("providerId", providerId);
    val query =
        "SELECT * FROM linked_account WHERE user_id = :userId and provider_id = :providerId";
    return DataAccessUtils.singleResult(
        jdbcTemplate.query(query, namedParameters, new LinkedAccountRowMapper()));
  }

  public LinkedAccount upsertLinkedAccount(LinkedAccount linkedAccount) {
    val query =
        "INSERT INTO linked_account (user_id, provider_id, refresh_token, expires, external_user_id)"
            + " VALUES (:userId, :providerId, :refreshToken, :expires, :externalUserId)"
            + " ON CONFLICT (user_id, provider_id) DO UPDATE SET"
            + " refresh_token = excluded.refresh_token,"
            + " expires = excluded.expires,"
            + " external_user_id = excluded.external_user_id"
            + " RETURNING id";

    val namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", linkedAccount.getUserId())
            .addValue("providerId", linkedAccount.getProviderId())
            .addValue("refreshToken", linkedAccount.getRefreshToken())
            .addValue("expires", linkedAccount.getExpires())
            .addValue("externalUserId", linkedAccount.getExternalUserId());

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    val generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return linkedAccount.withId(generatedKeyHolder.getKey().intValue());
  }

  public boolean deleteLinkedAccount(String userId, String providerId) {
    val query = "DELETE FROM linked_account WHERE user_id = :userId and provider_id = :providerId";
    val namedParameters =
        new MapSqlParameterSource().addValue("userId", userId).addValue("providerId", providerId);

    return jdbcTemplate.update(query, namedParameters) > 0;
  }

  private static class LinkedAccountRowMapper implements RowMapper<LinkedAccount> {

    @Override
    public LinkedAccount mapRow(ResultSet rs, int rowNum) throws SQLException {
      return LinkedAccount.builder()
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

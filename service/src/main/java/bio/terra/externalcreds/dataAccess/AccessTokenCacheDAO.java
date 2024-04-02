package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.AccessTokenCacheEntry;
import bio.terra.externalcreds.models.LinkedAccount;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.sql.Timestamp;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class AccessTokenCacheDAO {

  private static final RowMapper<AccessTokenCacheEntry> ACCESS_TOKEN_CACHE_ROW_MAPPER =
      ((rs, rowNum) ->
          new AccessTokenCacheEntry.Builder()
              .linkedAccountId(rs.getInt("linked_account_id"))
              .accessToken(rs.getString("access_token"))
              .expiresAt(rs.getTimestamp("expires_at").toInstant())
              .build());

  final NamedParameterJdbcTemplate jdbcTemplate;

  public AccessTokenCacheDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WithSpan
  public Optional<AccessTokenCacheEntry> getAccessTokenCacheEntry(LinkedAccount linkedAccount) {
    return linkedAccount
        .getId()
        .flatMap(
            linkedAccountId -> {
              var namedParameters =
                  new MapSqlParameterSource().addValue("linkedAccountId", linkedAccountId);
              var query =
                  "SELECT token.linked_account_id, token.access_token, token.expires_at "
                      + "  FROM access_token_cache token"
                      + "  WHERE token.linked_account_id = :linkedAccountId";
              return Optional.ofNullable(
                  DataAccessUtils.singleResult(
                      jdbcTemplate.query(query, namedParameters, ACCESS_TOKEN_CACHE_ROW_MAPPER)));
            });
  }

  @WithSpan
  public Optional<AccessTokenCacheEntry> getAccessTokenCacheEntry(
      String userId, Provider provider) {
    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("provider", provider.name());
    var query =
        "SELECT token.linked_account_id, token.access_token, token.expires_at"
            + " FROM access_token_cache token"
            + " INNER JOIN linked_account la ON la.id = token.linked_account_id"
            + " WHERE la.user_id = :userId"
            + " AND la.provider = :provider::provider_enum";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, ACCESS_TOKEN_CACHE_ROW_MAPPER)));
  }

  @WithSpan
  public AccessTokenCacheEntry upsertAccessTokenCacheEntry(
      AccessTokenCacheEntry accessTokenCacheEntry) {
    var query =
        "INSERT INTO access_token_cache (linked_account_id, access_token, expires_at)"
            + " VALUES (:linkedAccountId, :accessToken, :expiresAt)"
            + " ON CONFLICT (linked_account_id) DO UPDATE SET"
            + " linked_account_id = excluded.linked_account_id,"
            + " access_token = excluded.access_token,"
            + " expires_at = excluded.expires_at";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("linkedAccountId", accessTokenCacheEntry.getLinkedAccountId())
            .addValue("accessToken", accessTokenCacheEntry.getAccessToken())
            .addValue("expiresAt", Timestamp.from(accessTokenCacheEntry.getExpiresAt()));

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    var numUpdated = jdbcTemplate.update(query, namedParameters, generatedKeyHolder);
    if (numUpdated == 1) {
      return accessTokenCacheEntry;
    } else {
      throw new RuntimeException("Failed to upsert access token cache entry");
    }
  }

  /**
   * @param linkedAccountId id of the linked account
   * @return boolean whether a access token cache entry was deleted
   */
  @WithSpan
  public boolean deleteAccessTokenCacheEntry(int linkedAccountId) {
    var namedParameters = new MapSqlParameterSource("linkedAccountId", linkedAccountId);
    var query = "DELETE FROM access_token_cache WHERE linked_account_id = :linkedAccountId";
    return jdbcTemplate.update(query, namedParameters) > 0;
  }
}

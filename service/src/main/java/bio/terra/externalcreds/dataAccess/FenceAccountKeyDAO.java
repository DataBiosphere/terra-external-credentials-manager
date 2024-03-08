package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.FenceAccountKey;
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
public class FenceAccountKeyDAO {

  private static final RowMapper<FenceAccountKey> FENCE_ACCOUNT_KEY_ROW_MAPPER =
      ((rs, rowNum) ->
          new FenceAccountKey.Builder()
              .id(rs.getInt("id"))
              .linkedAccountId(rs.getInt("linked_account_id"))
              .keyJson(rs.getString("key_json"))
              .expiresAt(rs.getTimestamp("expires_at").toInstant())
              .build());

  final NamedParameterJdbcTemplate jdbcTemplate;

  public FenceAccountKeyDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<FenceAccountKey> getFenceAccountKey(LinkedAccount linkedAccount) {
    return linkedAccount
        .getId()
        .flatMap(
            linkedAccountId -> {
              var namedParameters =
                  new MapSqlParameterSource().addValue("linkedAccountId", linkedAccountId);
              var query =
                  "SELECT fence.id, fence.linked_account_id, fence.key_json, fence.expires_at "
                      + "  FROM fence_account_key fence"
                      + "  WHERE fence.linked_account_id = :linkedAccountId";
              return Optional.ofNullable(
                  DataAccessUtils.singleResult(
                      jdbcTemplate.query(query, namedParameters, FENCE_ACCOUNT_KEY_ROW_MAPPER)));
            });
  }

  @WithSpan
  public Optional<FenceAccountKey> getFenceAccountKey(String userId, Provider provider) {
    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("provider", provider.name());
    var query =
        "SELECT fence.id, fence.linked_account_id, fence.key_json, fence.expires_at FROM fence_account_key fence"
            + " INNER JOIN linked_account la ON la.id = fence.linked_account_id"
            + " WHERE la.user_id = :userId"
            + " AND la.provider = :provider::provider_enum";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, FENCE_ACCOUNT_KEY_ROW_MAPPER)));
  }

  @WithSpan
  public FenceAccountKey upsertFenceAccountKey(FenceAccountKey fenceAccountKey) {
    var query =
        "INSERT INTO fence_account_key (linked_account_id, key_json, expires_at)"
            + " VALUES (:linkedAccountId, :keyJson::jsonb, :expiresAt)"
            + " ON CONFLICT (linked_account_id) DO UPDATE SET"
            + " linked_account_id = excluded.linked_account_id,"
            + " key_json = excluded.key_json::jsonb,"
            + " expires_at = excluded.expires_at"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("linkedAccountId", fenceAccountKey.getLinkedAccountId())
            .addValue("keyJson", fenceAccountKey.getKeyJson())
            .addValue("expiresAt", Timestamp.from(fenceAccountKey.getExpiresAt()));

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return fenceAccountKey.withId(Objects.requireNonNull(generatedKeyHolder.getKey()).intValue());
  }

  /**
   * @param linkedAccountId id of the linked account
   * @return boolean whether a fence account key was deleted
   */
  @WithSpan
  public boolean deleteFenceAccountKey(int linkedAccountId) {
    var namedParameters = new MapSqlParameterSource("linkedAccountId", linkedAccountId);
    var query = "DELETE FROM fence_account_key WHERE linked_account_id = :linkedAccountId";
    return jdbcTemplate.update(query, namedParameters) > 0;
  }
}

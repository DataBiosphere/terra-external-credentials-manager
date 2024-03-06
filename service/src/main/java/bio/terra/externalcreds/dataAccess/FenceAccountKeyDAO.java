package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.FenceAccountKey;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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
              .expiresAt(rs.getTimestamp("expires_at"))
              .build());

  final NamedParameterJdbcTemplate jdbcTemplate;

  public FenceAccountKeyDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
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
  public FenceAccountKey insertFenceAccountKey(FenceAccountKey fenceAccountKey) {
    var query =
        "INSERT INTO fence_account_key (linked_account_id, key_json, expires_at)"
            + " VALUES (:linkedAccountId, :keyJson, :expiresAt)"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("linkedAccountId", fenceAccountKey.getLinkedAccountId())
            .addValue("keyJson", fenceAccountKey.getKeyJson())
            .addValue("expiresAt", fenceAccountKey.getExpiresAt());

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

package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.models.NihAccount;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
public class NihAccountDAO {

  private static final RowMapper<NihAccount> NIH_ACCOUNT_ROW_MAPPER =
      ((rs, rowNum) ->
          new NihAccount.Builder()
              .id(rs.getInt("id"))
              .userId(rs.getString("user_id"))
              .nihUsername(rs.getString("nih_username"))
              .expires(rs.getTimestamp("expires"))
              .build());

  final NamedParameterJdbcTemplate jdbcTemplate;

  public NihAccountDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @WithSpan
  public Optional<NihAccount> getNihAccount(String userId) {
    var namedParameters = new MapSqlParameterSource().addValue("userId", userId);
    var query = "SELECT * FROM nih_account WHERE user_id = :userId";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, NIH_ACCOUNT_ROW_MAPPER)));
  }

  @WithSpan
  public Optional<NihAccount> getNihAccountForUsername(String nihUsername) {
    var namedParameters = new MapSqlParameterSource().addValue("nihUsername", nihUsername);
    var query = "SELECT * FROM nih_account WHERE nih_username = :nihUsername";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, NIH_ACCOUNT_ROW_MAPPER)));
  }

  @WithSpan
  public List<NihAccount> getExpiredNihAccounts() {
    var namedParameters =
        new MapSqlParameterSource("expirationCutoff", Timestamp.from(Instant.now()));
    var query =
        "SELECT DISTINCT na.* FROM nih_account na"
            + " WHERE na.expires < :expirationCutoff ORDER BY id asc";
    return jdbcTemplate.query(query, namedParameters, NIH_ACCOUNT_ROW_MAPPER);
  }

  @WithSpan
  public List<NihAccount> getActiveNihAccounts() {
    var namedParameters =
        new MapSqlParameterSource("expirationCutoff", Timestamp.from(Instant.now()));
    var query =
        "SELECT DISTINCT na.* FROM nih_account na"
            + " WHERE na.expires > :expirationCutoff ORDER BY id asc";
    return jdbcTemplate.query(query, namedParameters, NIH_ACCOUNT_ROW_MAPPER);
  }

  @WithSpan
  public NihAccount upsertNihAccount(NihAccount nihAccount) {
    var query =
        "INSERT INTO nih_account (user_id, nih_username, expires)"
            + " VALUES (:userId, :nihUsername, :expires)"
            + " ON CONFLICT (user_id) DO UPDATE SET"
            + " nih_username = excluded.nih_username,"
            + " expires = excluded.expires"
            + " RETURNING id";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("userId", nihAccount.getUserId())
            .addValue("nihUsername", nihAccount.getNihUsername())
            .addValue("expires", nihAccount.getExpires());

    // generatedKeyHolder will hold the id returned by the query as specified by the RETURNING
    // clause
    var generatedKeyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(query, namedParameters, generatedKeyHolder);

    return nihAccount.withId(Objects.requireNonNull(generatedKeyHolder.getKey()).intValue());
  }

  /**
   * @param userId
   * @return boolean whether or not an account was found and deleted
   */
  @WithSpan
  public boolean deleteNihAccountIfExists(String userId) {
    var query = "DELETE FROM nih_account WHERE user_id = :userId";
    var namedParameters = new MapSqlParameterSource().addValue("userId", userId);

    return jdbcTemplate.update(query, namedParameters) > 0;
  }
}

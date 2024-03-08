package bio.terra.externalcreds.dataAccess;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.models.DistributedLock;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.sql.Timestamp;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class DistributedLockDAO {

  private static final RowMapper<DistributedLock> DISTRIBUTED_LOCK_ROW_MAPPER =
      ((rs, rowNum) ->
          new DistributedLock.Builder()
              .lockName(rs.getString("lock_name"))
              .userId(rs.getString("user_id"))
              .expiresAt(rs.getTimestamp("expires_at").toInstant())
              .build());

  final NamedParameterJdbcTemplate jdbcTemplate;

  public DistributedLockDAO(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * @param lockName The name of the lock, e.g {provider}-createKey
   * @param userId The Sam user id
   * @return Optional<DistributedLock> An optional containing the distributed lock with this
   *     lockName and userId (or empty)
   */
  @WithSpan
  @ReadTransaction
  public Optional<DistributedLock> getDistributedLock(String lockName, String userId) {
    var namedParameters =
        new MapSqlParameterSource().addValue("lockName", lockName).addValue("userId", userId);
    var query =
        "SELECT lock_name, user_id, expires_at FROM distributed_lock WHERE lock_name = :lockName AND user_id = :userId";
    return Optional.ofNullable(
        DataAccessUtils.singleResult(
            jdbcTemplate.query(query, namedParameters, DISTRIBUTED_LOCK_ROW_MAPPER)));
  }

  /**
   * @param distributedLock The DistributedLock to insert
   * @return distributedLock The DistributedLock that was inserted
   */
  @WithSpan
  @WriteTransaction
  public DistributedLock insertDistributedLock(DistributedLock distributedLock) {
    var query =
        "INSERT INTO distributed_lock (lock_name, user_id, expires_at)"
            + " VALUES (:lockName, :userId, :expiresAt)";

    var namedParameters =
        new MapSqlParameterSource()
            .addValue("lockName", distributedLock.getLockName())
            .addValue("userId", distributedLock.getUserId())
            .addValue("expiresAt", Timestamp.from(distributedLock.getExpiresAt()));

    jdbcTemplate.update(query, namedParameters);
    return distributedLock;
  }

  /**
   * @param lockName The name of the lock, e.g {provider}-createKey
   * @param userId The Sam user id
   * @return boolean whether a distributed lock was found and deleted
   */
  @WithSpan
  @WriteTransaction
  public boolean deleteDistributedLock(String lockName, String userId) {
    var query = "DELETE FROM distributed_lock WHERE lock_name = :lockName AND user_id = :userId";
    var namedParameters =
        new MapSqlParameterSource().addValue("lockName", lockName).addValue("userId", userId);

    return jdbcTemplate.update(query, namedParameters) > 0;
  }
}

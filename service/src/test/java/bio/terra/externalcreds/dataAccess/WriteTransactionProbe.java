package bio.terra.externalcreds.dataAccess;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WriteTransactionProbe {
  private final JdbcTemplate jdbcTemplate;

  public WriteTransactionProbe(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void createTestTable() {
    jdbcTemplate.execute("create table transaction_test (id integer primary key, probe integer)");
  }

  public void dropTable() {
    jdbcTemplate.execute("drop table transaction_test");
  }

  public void insertTestRecord(int id, int probe) {
    jdbcTemplate.update("insert into transaction_test(id, probe) values (?, ?)", id, probe);
  }

  public Integer getTestProbeValue(int id) {
    return jdbcTemplate.queryForObject(
        "select probe from transaction_test where id = ?", Integer.class, id);
  }

  @WriteTransaction
  public void successfulWriteTransaction(CyclicBarrier barrier, int id)
      throws BrokenBarrierException, InterruptedException {
    Integer probeValue = getTestProbeValue(id);
    barrier.await();
    jdbcTemplate.update("update transaction_test set probe = ? where id = ?", probeValue + 1, id);
    barrier.await();
  }

  @WriteTransaction
  public void retriedWriteTransaction(CyclicBarrier barrier, int id)
      throws BrokenBarrierException, InterruptedException {
    Integer probeValue = getTestProbeValue(id);
    barrier.await();
    barrier.await();
    jdbcTemplate.update("update transaction_test set probe = ? where id = ?", probeValue + 1, id);
  }
}

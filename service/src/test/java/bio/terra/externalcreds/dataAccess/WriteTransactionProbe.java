package bio.terra.externalcreds.dataAccess;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.CannotCreateTransactionException;

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
    barrier.await(); // wait 1
    incrementTestProbeValue(id, probeValue);
  }

  @WriteTransaction
  public void retriedWriteTransaction(CyclicBarrier barrier, int id)
      throws BrokenBarrierException, InterruptedException {
    Integer probeValue = getTestProbeValue(id);
    barrier.await(); // wait 1 and 3
    barrier.await(); // wait 2 and 4
    incrementTestProbeValue(id, probeValue);
  }

  private AtomicInteger throwCannotCreateTransactionExceptionCount = new AtomicInteger(0);

  @WriteTransaction
  public void throwCannotCreateTransactionException() {
    throwCannotCreateTransactionExceptionCount.incrementAndGet();
    throw new CannotCreateTransactionException("test");
  }

  public int getThrowCannotCreateTransactionExceptionCount() {
    return throwCannotCreateTransactionExceptionCount.get();
  }

  private AtomicInteger throwCannotSerializeTransactionExceptionCount = new AtomicInteger(0);

  @WriteTransaction
  public void throwCannotSerializeTransactionException() {
    throwCannotSerializeTransactionExceptionCount.incrementAndGet();
    throw new CannotSerializeTransactionException("test");
  }

  public int getThrowCannotSerializeTransactionExceptionCount() {
    return throwCannotSerializeTransactionExceptionCount.get();
  }

  private void incrementTestProbeValue(int id, Integer probeValue) {
    jdbcTemplate.update("update transaction_test set probe = ? where id = ?", probeValue + 1, id);
  }
}

package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.BaseTest;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.transaction.CannotCreateTransactionException;

public class WriteTransactionTest extends BaseTest {
  @Autowired private WriteTransactionProbe writeTransactionProbe;

  @BeforeEach
  public void createTable() {
    writeTransactionProbe.createTestTable();
  }

  @AfterEach
  void dropTable() {
    writeTransactionProbe.dropTable();
  }

  /**
   * This tests that in a pair of competing write transactions (see {@link WriteTransaction}) one
   * fails and is retried. This uses a {@link CyclicBarrier} to coordinate 2 threads such that one
   * must fail if they are both using Isolation.SERIALIZABLE. The failure must then be retried in
   * order to achieve success.
   *
   * @throws InterruptedException
   */
  @Test
  public void testWriteTransactionAnnotation() throws InterruptedException {
    int probeValue = 22;
    int probeId = 1;

    writeTransactionProbe.insertTestRecord(probeId, probeValue);

    // There are 2 threads using this barrier and each must wait a total of 4 times. The successful
    // thread gets the probe value, waits, increments the probe value in the db and commits then
    // waits 3 more times (to match the number of waits of the retried thread). The retried thread
    // gets the probe value, waits (so now both threads have the same value), waits again so the
    // successful thread has committed the change, then attempts to increment in the db and commit.
    // This should fail because the transaction could not be serialized. It should then be retried
    // which has 2 more waits (because it is the same code that ran the first time).
    // At the end, the probe value should have been incremented twice. If the retried thread did not
    // actually fail then one increment will clobber the other. If the retried thread does not retry
    // then there will only be 1 increment.
    CyclicBarrier barrier = new CyclicBarrier(2);

    Thread successfulThread =
        new Thread(
            () -> {
              try {
                writeTransactionProbe.successfulWriteTransaction(barrier, probeId);
                barrier.await(); // wait 2
                barrier.await(); // wait 3
                barrier.await(); // wait 4
              } catch (Exception e) {
                e.printStackTrace();
              }
            });

    Thread retriedThread =
        new Thread(
            () -> {
              try {
                writeTransactionProbe.retriedWriteTransaction(barrier, probeId);
              } catch (Exception e) {
                e.printStackTrace();
              }
            });

    successfulThread.start();
    retriedThread.start();

    successfulThread.join(5000);
    retriedThread.join(5000);

    Assertions.assertEquals(probeValue + 2, writeTransactionProbe.getTestProbeValue(probeId));
  }

  @Test
  public void testCannotSerializeTransactionExceptionRetries() {
    Assertions.assertThrows(
        CannotSerializeTransactionException.class,
        () -> writeTransactionProbe.throwCannotSerializeTransactionException());
    Assertions.assertEquals(
        100, writeTransactionProbe.getThrowCannotSerializeTransactionExceptionCount());
  }

  @Test
  public void testCannotCreateTransactionExceptionRetries() {
    Assertions.assertThrows(
        CannotCreateTransactionException.class,
        () -> writeTransactionProbe.throwCannotCreateTransactionException());
    Assertions.assertEquals(
        4, writeTransactionProbe.getThrowCannotCreateTransactionExceptionCount());
  }
}

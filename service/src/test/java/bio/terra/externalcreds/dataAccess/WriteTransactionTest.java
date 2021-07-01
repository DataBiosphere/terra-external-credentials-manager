package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.BaseTest;
import java.util.concurrent.CyclicBarrier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

  @Test
  public void testWriteTransactionAnnotation() throws InterruptedException {
    int probeValue = 22;
    int probeId = 1;

    writeTransactionProbe.insertTestRecord(probeId, probeValue);

    CyclicBarrier barrier = new CyclicBarrier(2);

    Thread successfulThread =
        new Thread(
            () -> {
              try {
                writeTransactionProbe.successfulWriteTransaction(barrier, probeId);
                barrier.await();
                barrier.await();
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

    successfulThread.join();
    retriedThread.join();

    Assertions.assertEquals(probeValue + 2, writeTransactionProbe.getTestProbeValue(probeId));
  }
}

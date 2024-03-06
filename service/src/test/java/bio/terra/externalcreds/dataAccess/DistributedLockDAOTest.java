package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.models.DistributedLock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DistributedLockDAOTest extends BaseTest {

  @Autowired private DistributedLockDAO distributedLockDAO;
  private final String testLockName = "provider-createKey";
  private final DistributedLock testDistributedLock =
      new DistributedLock.Builder()
          .lockName(testLockName)
          .userId(UUID.randomUUID().toString())
          .expiresAt(TestUtils.getRandomTimestamp())
          .build();

  @Test
  void testGetMissingDistributedLock() {
    var shouldBeEmpty = distributedLockDAO.getDistributedLock(testLockName, "nonexistent_user_id");
    assertEmpty(shouldBeEmpty);
  }

  @Nested
  class CreateDistributedLock {

    @Test
    void testCreateAndGetDistributedLock() {
      DistributedLock savedLock = distributedLockDAO.insertDistributedLock(testDistributedLock);
      assertEquals(testDistributedLock, savedLock);
      var loadedDistributedLock =
          distributedLockDAO.getDistributedLock(savedLock.getLockName(), savedLock.getUserId());
      assertEquals(Optional.of(savedLock), loadedDistributedLock);
    }
  }

  @Nested
  class DeleteDistributedLock {

    @Test
    void testDeleteDistributedLock() {
      DistributedLock savedLock = distributedLockDAO.insertDistributedLock(testDistributedLock);
      assertPresent(
          distributedLockDAO.getDistributedLock(savedLock.getLockName(), savedLock.getUserId()));
      assertTrue(
          distributedLockDAO.deleteDistributedLock(savedLock.getLockName(), savedLock.getUserId()));
      assertEmpty(
          distributedLockDAO.getDistributedLock(savedLock.getLockName(), savedLock.getUserId()));
    }

    @Test
    void testDeleteNonexistentDistributedLock() {

      assertFalse(distributedLockDAO.deleteDistributedLock(testLockName, "nonexistent_user_id"));
    }
  }
}

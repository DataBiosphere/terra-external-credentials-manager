package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FenceAccountKeyDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private FenceAccountKeyDAO fenceAccountKeyDAO;

  @Test
  void testGetMissingFenceAccountKey() {
    var shouldBeEmpty =
        fenceAccountKeyDAO.getFenceAccountKey("nonexistent_user_id", "nonexistent_provider_name");
    assertEmpty(shouldBeEmpty);
  }

  @Nested
  class CreateFenceAccountKey {

    @Test
    void testCreateAndGetFenceAccountKey() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      assertPresent(savedAccount.getId());

      var fenceAccountKey = TestUtils.createRandomFenceAccountKey();
      var savedFenceAccountKey =
          fenceAccountKeyDAO.insertFenceAccountKey(
              fenceAccountKey.withLinkedAccountId(savedAccount.getId().get()));
      assertPresent(savedFenceAccountKey.getId());

      assertEquals(
          fenceAccountKey
              .withId(savedFenceAccountKey.getId())
              .withLinkedAccountId(savedFenceAccountKey.getLinkedAccountId())
              .withKeyJson(savedFenceAccountKey.getKeyJson()),
          savedFenceAccountKey);

      var loadedFenceAccountKey =
          fenceAccountKeyDAO.getFenceAccountKey(
              savedAccount.getUserId(), savedAccount.getProviderName());
      assertEquals(Optional.of(savedFenceAccountKey), loadedFenceAccountKey);
    }
  }

  @Nested
  class DeleteFenceAccountKey {

    @Test
    void testDeleteFenceAccountKey() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      assertPresent(savedAccount.getId());
      fenceAccountKeyDAO.insertFenceAccountKey(
          TestUtils.createRandomFenceAccountKey().withLinkedAccountId(savedAccount.getId().get()));

      assertPresent(
          fenceAccountKeyDAO.getFenceAccountKey(
              savedAccount.getUserId(), savedAccount.getProviderName()));
      assertTrue(fenceAccountKeyDAO.deleteFenceAccountKey(savedAccount.getId().get()));
      assertEmpty(
          fenceAccountKeyDAO.getFenceAccountKey(
              savedAccount.getUserId(), savedAccount.getProviderName()));
    }

    @Test
    void testDeleteNonexistentFenceAccountKey() {
      assertFalse(fenceAccountKeyDAO.deleteFenceAccountKey(-1));
    }
  }
}

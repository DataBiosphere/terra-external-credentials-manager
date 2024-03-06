package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.*;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.generated.model.Provider;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FenceAccountKeyDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private FenceAccountKeyDAO fenceAccountKeyDAO;

  @Test
  void testGetMissingFenceAccountKey() {
    var shouldBeEmpty = fenceAccountKeyDAO.getFenceAccountKey("nonexistent_user_id", Provider.RAS);
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
          fenceAccountKeyDAO.upsertFenceAccountKey(
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
              savedAccount.getUserId(), savedAccount.getProvider());
      assertEquals(Optional.of(savedFenceAccountKey), loadedFenceAccountKey);
    }

    @Test
    void testUpsertFenceAccountKey() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      assertPresent(savedAccount.getId());

      var fenceAccountKey =
          TestUtils.createRandomFenceAccountKey().withLinkedAccountId(savedAccount.getId().get());
      var savedFenceAccountKey = fenceAccountKeyDAO.upsertFenceAccountKey(fenceAccountKey);
      assertPresent(savedFenceAccountKey.getId());

      assertEquals(
          fenceAccountKey
              .withId(savedFenceAccountKey.getId())
              .withLinkedAccountId(savedFenceAccountKey.getLinkedAccountId())
              .withKeyJson(savedFenceAccountKey.getKeyJson()),
          savedFenceAccountKey);

      var newKey = "{\"key\": \"new value\"}";
      var updatedFenceAccountKey =
          fenceAccountKeyDAO.upsertFenceAccountKey(fenceAccountKey.withKeyJson(newKey));

      assertEquals(
          fenceAccountKey
              .withId(savedFenceAccountKey.getId())
              .withLinkedAccountId(savedFenceAccountKey.getLinkedAccountId())
              .withKeyJson(newKey),
          updatedFenceAccountKey);
    }
  }

  @Nested
  class DeleteFenceAccountKey {

    @Test
    void testDeleteFenceAccountKey() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      assertPresent(savedAccount.getId());
      fenceAccountKeyDAO.upsertFenceAccountKey(
          TestUtils.createRandomFenceAccountKey().withLinkedAccountId(savedAccount.getId().get()));

      assertPresent(
          fenceAccountKeyDAO.getFenceAccountKey(
              savedAccount.getUserId(), savedAccount.getProvider()));
      assertTrue(fenceAccountKeyDAO.deleteFenceAccountKey(savedAccount.getId().get()));
      assertEmpty(
          fenceAccountKeyDAO.getFenceAccountKey(
              savedAccount.getUserId(), savedAccount.getProvider()));
    }

    @Test
    void testDeleteNonexistentFenceAccountKey() {
      assertFalse(fenceAccountKeyDAO.deleteFenceAccountKey(-1));
    }
  }
}

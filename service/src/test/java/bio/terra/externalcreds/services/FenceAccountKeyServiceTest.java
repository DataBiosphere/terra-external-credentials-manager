package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.dataAccess.FenceAccountKeyDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.generated.model.Provider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

class FenceAccountKeyServiceTest extends BaseTest {

  @Nested
  @TestComponent
  class GetFenceAccountKey {
    @Autowired FenceAccountKeyService fenceAccountKeyService;
    @Autowired FenceAccountKeyDAO fenceAccountKeyDAO;
    @Autowired LinkedAccountDAO linkedAccountDAO;

    @Test
    void testGetFenceAccountKey() {
      var linkedAccount = TestUtils.createRandomLinkedAccount(Provider.FENCE);
      var fenceAccountKey = TestUtils.createRandomFenceAccountKey();

      var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
      var savedFenceAccountKey =
          fenceAccountKeyDAO.upsertFenceAccountKey(
              fenceAccountKey.withLinkedAccountId(savedLinkedAccount.getId().get()));

      var loadedFenceAccountKey =
          fenceAccountKeyService.getFenceAccountKey(
              linkedAccount.getUserId(), linkedAccount.getProvider());

      assertPresent(loadedFenceAccountKey);
      assertEquals(fenceAccountKey.getKeyJson(), savedFenceAccountKey.getKeyJson());
      assertEquals(fenceAccountKey.getExpiresAt(), savedFenceAccountKey.getExpiresAt());
    }

    @Test
    void testGetFenceAccountKeyNoLinkedAccount() {
      var userId = "nonexistent_user_id";
      var provider = Provider.FENCE;
      assertEmpty(fenceAccountKeyService.getFenceAccountKey(userId, provider));
    }

    @Test
    void testGetFenceAccountKeyLinkedAccountNoFenceAccount() {
      var linkedAccount = TestUtils.createRandomLinkedAccount(Provider.FENCE);
      linkedAccountDAO.upsertLinkedAccount(linkedAccount);
      assertEmpty(
          fenceAccountKeyService.getFenceAccountKey(
              linkedAccount.getUserId(), linkedAccount.getProvider()));
    }
  }
}

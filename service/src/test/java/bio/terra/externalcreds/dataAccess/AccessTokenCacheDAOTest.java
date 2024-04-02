package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.generated.model.Provider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AccessTokenCacheDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private AccessTokenCacheDAO accessTokenCacheDAO;

  @Test
  void testGetMissingAccessTokenCacheEntry() {
    var shouldBeEmpty =
        accessTokenCacheDAO.getAccessTokenCacheEntry("nonexistent_user_id", Provider.RAS);
    assertEmpty(shouldBeEmpty);
  }

  @Nested
  class CreateAccessTokenCacheEntry {

    @Test
    void testCreateAndGetAccessTokenCacheEntry() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      assertPresent(savedAccount.getId());

      var accessTokenCacheEntry =
          TestUtils.createRandomAccessTokenCacheEntry()
              .withLinkedAccountId(savedAccount.getId().get());
      var savedAccessTokenCacheEntry =
          accessTokenCacheDAO.upsertAccessTokenCacheEntry(accessTokenCacheEntry);

      assertEquals(accessTokenCacheEntry, savedAccessTokenCacheEntry);

      var loadedAccessTokenCacheEntry =
          accessTokenCacheDAO.getAccessTokenCacheEntry(
              savedAccount.getUserId(), savedAccount.getProvider());
      assertEquals(Optional.of(savedAccessTokenCacheEntry), loadedAccessTokenCacheEntry);

      var loadedByLinkedAccount = accessTokenCacheDAO.getAccessTokenCacheEntry(savedAccount);
      assertEquals(Optional.of(savedAccessTokenCacheEntry), loadedByLinkedAccount);
    }

    @Test
    void testUpsertAccessTokenCacheEntry() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      assertPresent(savedAccount.getId());

      var accessTokenCacheEntry =
          TestUtils.createRandomAccessTokenCacheEntry()
              .withLinkedAccountId(savedAccount.getId().get());
      var savedAccessTokenCacheEntry =
          accessTokenCacheDAO.upsertAccessTokenCacheEntry(accessTokenCacheEntry);

      assertEquals(accessTokenCacheEntry, savedAccessTokenCacheEntry);

      var newToken = UUID.randomUUID().toString();
      var newExpiresAt = accessTokenCacheEntry.getExpiresAt().plusSeconds(1);

      accessTokenCacheDAO.upsertAccessTokenCacheEntry(
          accessTokenCacheEntry.withAccessToken(newToken).withExpiresAt(newExpiresAt));

      var updatedAccessTokenCacheEntry = accessTokenCacheDAO.getAccessTokenCacheEntry(savedAccount);
      assertPresent(updatedAccessTokenCacheEntry);
      assertEquals(
          accessTokenCacheEntry.withAccessToken(newToken).withExpiresAt(newExpiresAt),
          updatedAccessTokenCacheEntry.get());
    }
  }

  @Nested
  class DeleteAccessTokenCacheEntry {

    @Test
    void testDeleteAccessTokenCacheEntry() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      assertPresent(savedAccount.getId());
      accessTokenCacheDAO.upsertAccessTokenCacheEntry(
          TestUtils.createRandomAccessTokenCacheEntry()
              .withLinkedAccountId(savedAccount.getId().get()));

      assertPresent(
          accessTokenCacheDAO.getAccessTokenCacheEntry(
              savedAccount.getUserId(), savedAccount.getProvider()));
      assertTrue(accessTokenCacheDAO.deleteAccessTokenCacheEntry(savedAccount.getId().get()));
      assertEmpty(
          accessTokenCacheDAO.getAccessTokenCacheEntry(
              savedAccount.getUserId(), savedAccount.getProvider()));
    }

    @Test
    void testDeleteNonexistentAccessTokenCacheEntry() {
      assertFalse(accessTokenCacheDAO.deleteAccessTokenCacheEntry(-1));
    }
  }
}

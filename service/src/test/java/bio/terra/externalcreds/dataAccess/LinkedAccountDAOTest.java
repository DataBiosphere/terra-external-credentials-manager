package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.models.GA4GHPassport;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class LinkedAccountDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;

  @Test
  void testGetMissingLinkedAccount() {
    var shouldBeEmpty = linkedAccountDAO.getLinkedAccount("", "");
    assertEmpty(shouldBeEmpty);
  }

  @Test
  void testInsertAndGetLinkedAccount() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    assertTrue(savedLinkedAccount.getId().isPresent());
    assertEquals(linkedAccount.withId(savedLinkedAccount.getId()), savedLinkedAccount);

    var loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertEquals(Optional.of(savedLinkedAccount), loadedLinkedAccount);
  }

  @Test
  void testUpsertUpdatedLinkedAccount() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    var createdLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);

    var updatedLinkedAccount =
        linkedAccountDAO.upsertLinkedAccount(
            linkedAccount
                .withRefreshToken("different_refresh")
                .withExpires(new Timestamp(200))
                .withExternalUserId(UUID.randomUUID().toString()));

    assertEquals(createdLinkedAccount.getId(), updatedLinkedAccount.getId());
    assertNotEquals(createdLinkedAccount.getRefreshToken(), updatedLinkedAccount.getRefreshToken());
    assertNotEquals(
        createdLinkedAccount.getExternalUserId(), updatedLinkedAccount.getExternalUserId());
    assertNotEquals(createdLinkedAccount.getExpires(), updatedLinkedAccount.getExpires());

    var loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertEquals(Optional.of(updatedLinkedAccount), loadedLinkedAccount);
  }

  @Nested
  class DeleteLinkedAccount {

    @Test
    void testDeleteLinkedAccountIfExists() {
      var createdLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      var deletionSucceeded =
          linkedAccountDAO.deleteLinkedAccountIfExists(
              createdLinkedAccount.getUserId(), createdLinkedAccount.getProviderId());
      assertTrue(deletionSucceeded);
      assertEmpty(
          linkedAccountDAO.getLinkedAccount(
              createdLinkedAccount.getUserId(), createdLinkedAccount.getProviderId()));
    }

    @Test
    void testDeleteNonexistentLinkedAccount() {
      var userId = UUID.randomUUID().toString();
      var deletionSucceeded = linkedAccountDAO.deleteLinkedAccountIfExists(userId, "fake_provider");
      assertEmpty(linkedAccountDAO.getLinkedAccount(userId, "fake_provider"));
      assertFalse(deletionSucceeded);
    }

    @Test
    void testAlsoDeletesPassport() {
      var savedLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      assertPresent(savedLinkedAccount.getId());
      var passport =
          new GA4GHPassport.Builder()
              .linkedAccountId(savedLinkedAccount.getId())
              .expires(new Timestamp(100))
              .jwt("jwt")
              .build();
      passportDAO.insertPassport(passport);
      linkedAccountDAO.deleteLinkedAccountIfExists(
          savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderId());
      assertEmpty(
          passportDAO.getPassport(
              savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderId()));
    }
  }
}

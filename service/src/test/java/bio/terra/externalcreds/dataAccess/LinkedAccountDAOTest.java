package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class LinkedAccountDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;

  private LinkedAccount linkedAccount;

  @BeforeEach
  void setup() {
    linkedAccount =
        LinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .externalUserId("externalUser")
            .build();
  }

  @Test
  void testMissingLinkedAccount() {
    var shouldBeNull = linkedAccountDAO.getLinkedAccount("", "");
    assertNull(shouldBeNull);
  }

  @Test
  void testCreateAndGetLinkedAccount() {
    var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    assertTrue(savedLinkedAccount.getId() > 0);
    assertEquals(linkedAccount.withId(savedLinkedAccount.getId()), savedLinkedAccount);

    var loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertEquals(savedLinkedAccount, loadedLinkedAccount);
  }

  @Test
  void testUpsertLinkedAccounts() {
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
    assertEquals(updatedLinkedAccount, loadedLinkedAccount);
  }

  @Nested
  class DeleteLinkedAccount {

    @Test
    void testDeleteLinkedAccountIfExists() {
      var createdLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
      var deletionSucceeded =
          linkedAccountDAO.deleteLinkedAccountIfExists(
              createdLinkedAccount.getUserId(), createdLinkedAccount.getProviderId());
      assertTrue(deletionSucceeded);
      assertNull(
          linkedAccountDAO.getLinkedAccount(
              createdLinkedAccount.getUserId(), createdLinkedAccount.getProviderId()));
    }

    @Test
    void testDeleteNonexistentLinkedAccount() {
      var userId = UUID.randomUUID().toString();
      var deletionSucceeded = linkedAccountDAO.deleteLinkedAccountIfExists(userId, "fake_provider");
      assertNull(linkedAccountDAO.getLinkedAccount(userId, "fake_provider"));
      assertFalse(deletionSucceeded);
    }

    @Test
    void testAlsoDeletesPassport() {
      var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
      var passport =
          GA4GHPassport.builder()
              .linkedAccountId(savedLinkedAccount.getId())
              .expires(new Timestamp(100))
              .jwt("jwt")
              .build();
      passportDAO.insertPassport(passport);
      linkedAccountDAO.deleteLinkedAccountIfExists(
          savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderId());
      assertTrue(passportDAO.getPassport(savedLinkedAccount.getId()).isEmpty());
    }
  }
}

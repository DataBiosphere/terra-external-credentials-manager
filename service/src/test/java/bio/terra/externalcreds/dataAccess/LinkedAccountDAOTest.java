package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

public class LinkedAccountDAOTest extends BaseTest {
  @Autowired private LinkedAccountDAO linkedAccountDAO;

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
    val shouldBeNull = linkedAccountDAO.getLinkedAccount("", "");
    assertNull(shouldBeNull);
  }

  @Test
  @Transactional
  @Rollback
  void testCreateAndGetLinkedAccount() {
    val savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    assertTrue(savedLinkedAccount.getId() > 0);
    assertEquals(linkedAccount, savedLinkedAccount.withId(0));

    val loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertEquals(savedLinkedAccount, loadedLinkedAccount);
  }

  @Test
  @Transactional
  @Rollback
  void testUpsertLinkedAccounts() {
    val createdLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    assertTrue(createdLinkedAccount.getId() > 0);
    assertEquals(linkedAccount, createdLinkedAccount.withId(0));

    val updatedLinkedAccount =
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

    val loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertEquals(updatedLinkedAccount, loadedLinkedAccount);
  }

  @Nested
  class DeleteLinkedAccount {

    @Test
    @Transactional
    @Rollback
    void testDeleteLinkedAccountIfExists() {
      val createdLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
      val deletionSucceeded =
          linkedAccountDAO.deleteLinkedAccountIfExists(
              createdLinkedAccount.getUserId(), createdLinkedAccount.getProviderId());
      assertTrue(deletionSucceeded);
      assertNull(
          linkedAccountDAO.getLinkedAccount(
              createdLinkedAccount.getUserId(), createdLinkedAccount.getProviderId()));
    }

    @Test
    @Transactional
    @Rollback
    void testDeleteNonexistentLinkedAccount() {
      val userId = UUID.randomUUID().toString();
      val deletionSucceeded = linkedAccountDAO.deleteLinkedAccountIfExists(userId, "fake_provider");
      assertNull(linkedAccountDAO.getLinkedAccount(userId, "fake_provider"));
      assertFalse(deletionSucceeded);
    }

    @Test
    @Transactional
    @Rollback
    void testCascadingDelete() {}
  }
}

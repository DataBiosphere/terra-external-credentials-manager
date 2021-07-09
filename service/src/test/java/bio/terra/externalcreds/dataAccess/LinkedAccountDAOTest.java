package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.val;
import org.junit.jupiter.api.*;
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
    Assertions.assertNull(shouldBeNull);
  }

  @Test
  @Transactional
  @Rollback
  void testCreateAndGetLinkedAccount() {
    val savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    Assertions.assertTrue(savedLinkedAccount.getId() > 0);
    Assertions.assertEquals(linkedAccount, savedLinkedAccount.withId(0));

    val loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    Assertions.assertEquals(savedLinkedAccount, loadedLinkedAccount);
  }

  @Test
  @Transactional
  @Rollback
  void testUpsertLinkedAccounts() {
    val createdLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    Assertions.assertTrue(createdLinkedAccount.getId() > 0);
    Assertions.assertEquals(linkedAccount, createdLinkedAccount.withId(0));

    val updatedLinkedAccount =
        linkedAccountDAO.upsertLinkedAccount(
            linkedAccount
                .withRefreshToken("different_refresh")
                .withExpires(new Timestamp(200))
                .withExternalUserId(UUID.randomUUID().toString()));

    Assertions.assertEquals(createdLinkedAccount.getId(), updatedLinkedAccount.getId());
    Assertions.assertNotEquals(
        createdLinkedAccount.getRefreshToken(), updatedLinkedAccount.getRefreshToken());
    Assertions.assertNotEquals(
        createdLinkedAccount.getExternalUserId(), updatedLinkedAccount.getExternalUserId());
    Assertions.assertNotEquals(
        createdLinkedAccount.getExpires(), updatedLinkedAccount.getExpires());

    val loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    Assertions.assertEquals(updatedLinkedAccount, loadedLinkedAccount);
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
      Assertions.assertTrue(deletionSucceeded);
      Assertions.assertNull(
          linkedAccountDAO.getLinkedAccount(
              createdLinkedAccount.getUserId(), createdLinkedAccount.getProviderId()));
    }

    @Test
    @Transactional
    @Rollback
    void testDeleteNonexistentLinkedAccount() {
      val userId = UUID.randomUUID().toString();
      val deletionSucceeded =
          linkedAccountDAO.deleteLinkedAccountIfExists(userId, "fake_provider");
      Assertions.assertNull(
          linkedAccountDAO.getLinkedAccount(
                  userId, "fake_provider"));
      Assertions.assertFalse(deletionSucceeded);
    }

    @Test
    @Transactional
    @Rollback
    void testCascadingDelete() {

    }
  }
}

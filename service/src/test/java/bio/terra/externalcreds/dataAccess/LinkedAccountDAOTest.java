package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

public class LinkedAccountDAOTest extends BaseTest {
  @Autowired private LinkedAccountDAO linkedAccountDAO;

  @Test
  void testMissingLinkedAccount() {
    LinkedAccount shouldBeNull = linkedAccountDAO.getLinkedAccount("", "");
    Assertions.assertNull(shouldBeNull);
  }

  @Test
  @Transactional
  @Rollback
  void testCreateAndGetLinkedAccount() {
    LinkedAccount linkedAccount =
        LinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .externalUserId("externalUser")
            .build();
    LinkedAccount savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    Assertions.assertTrue(savedLinkedAccount.getId() > 0);
    Assertions.assertEquals(linkedAccount, savedLinkedAccount.withId(0));

    LinkedAccount loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    Assertions.assertEquals(savedLinkedAccount, loadedLinkedAccount);
  }

  @Test
  @Transactional
  @Rollback
  void testUpsertLinkedAccounts() {
    LinkedAccount linkedAccount =
        LinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .build();
    LinkedAccount createdLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    Assertions.assertTrue(createdLinkedAccount.getId() > 0);
    Assertions.assertEquals(linkedAccount, createdLinkedAccount.withId(0));

    LinkedAccount updatedLinkedAccount =
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

    LinkedAccount loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    Assertions.assertEquals(updatedLinkedAccount, loadedLinkedAccount);
  }
}

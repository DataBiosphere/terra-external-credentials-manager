package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
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
    val linkedAccount =
        LinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .externalUserId("externalUser")
            .build();
    linkedAccountDAO.createLinkedAccount(linkedAccount);

    val savedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    Assertions.assertTrue(savedLinkedAccount.getId() > 0);
    Assertions.assertEquals(linkedAccount, savedLinkedAccount.withId(0));
  }

  @Test
  @Transactional
  @Rollback
  void testDuplicateLinkedAccounts() {
    val linkedAccount =
        LinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .build();
    linkedAccountDAO.createLinkedAccount(linkedAccount);
    Assertions.assertThrows(
        DuplicateKeyException.class, () -> linkedAccountDAO.createLinkedAccount(linkedAccount));
  }
}

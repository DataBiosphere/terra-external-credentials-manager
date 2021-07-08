package bio.terra.externalcreds.service;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.LinkedAccountService;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class LinkedAccountServiceTest extends BaseTest {
  @Autowired private LinkedAccountService linkedAccountService;
  @Autowired private LinkedAccountDAO linkedAccountDAO;

  @Test
  void testGetLinkedAccount() {
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
        linkedAccountService.getLinkedAccount(
            linkedAccount.getUserId(), linkedAccount.getProviderId());
    Assertions.assertEquals(linkedAccount, savedLinkedAccount.withId(0));
  }
}

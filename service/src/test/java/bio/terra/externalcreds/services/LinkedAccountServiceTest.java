package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.LinkedAccountService;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class LinkedAccountServiceTest extends BaseTest {
  @Autowired private LinkedAccountService linkedAccountService;
  @Autowired private LinkedAccountDAO linkedAccountDAO;

  @Test
  void testGetLinkedAccount() {
    var linkedAccount =
        LinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .externalUserId("externalUser")
            .build();
    linkedAccountDAO.upsertLinkedAccount(linkedAccount);

    var savedLinkedAccount =
        linkedAccountService.getLinkedAccount(
            linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertEquals(linkedAccount, savedLinkedAccount.withId(0));
  }
}

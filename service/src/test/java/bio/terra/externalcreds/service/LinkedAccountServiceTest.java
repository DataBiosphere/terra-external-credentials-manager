package bio.terra.externalcreds.service;

import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.LinkedAccountService;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

public class LinkedAccountServiceTest extends BaseTest {
  @Autowired private LinkedAccountService linkedAccountService;

  @MockBean private LinkedAccountDAO linkedAccountDAO;

  @Test
  void testGetLinkedAccount() {
    LinkedAccount inputLinkedAccount =
        LinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .externalUserId("externalUser")
            .build();
    when(linkedAccountDAO.getLinkedAccount(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(inputLinkedAccount);

    LinkedAccount outputLinkedAccount = linkedAccountService.getLinkedAccount("", "");

    Assertions.assertEquals(inputLinkedAccount, outputLinkedAccount);
  }
}

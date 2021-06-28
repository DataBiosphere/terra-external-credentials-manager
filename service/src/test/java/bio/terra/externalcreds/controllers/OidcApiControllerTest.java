package bio.terra.externalcreds.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.SamService;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

public class OidcApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockBean private LinkedAccountService linkedAccountService;
  @MockBean private SamService samService;

  @Test
  void testGetLink() throws Exception {
    LinkedAccount inputLinkedAccount =
        LinkedAccount.builder()
            .expires(Timestamp.valueOf("2007-09-23 10:10:10.0"))
            .providerId("testProvider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .externalUserId("externalUser")
            .build();

    when(samService.getUserIdFromSam()).thenReturn("");
    when(linkedAccountService.getLinkedAccount(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(inputLinkedAccount);

    mvc.perform(get("/api/oidc/v1/testProvider"))
        .andExpect(
            content()
                .json(
                    "{\"externalUserId\":\"externalUser\", \"expirationTimestamp\":\"2007-09-23T14:10:10Z\"}"));
  }
}

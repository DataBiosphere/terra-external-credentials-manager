package bio.terra.externalcreds.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.SamService;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
            .userId(UUID.randomUUID().toString())
            .providerId("testProvider")
            .externalUserId("externalUser")
            .expires(Timestamp.valueOf("2007-09-23 10:10:10.0"))
            .build();

    when(samService.getUserIdFromSam()).thenReturn(inputLinkedAccount.getUserId());
    when(linkedAccountService.getLinkedAccount(
            eq(inputLinkedAccount.getUserId()), eq(inputLinkedAccount.getProviderId())))
        .thenReturn(inputLinkedAccount);

    mvc.perform(get("/api/oidc/v1/" + inputLinkedAccount.getProviderId()))
        .andExpect(
            content()
                .json(
                    "{\"externalUserId\":\""
                        + inputLinkedAccount.getExternalUserId()
                        + "\", \"expirationTimestamp\":\""
                        + OffsetDateTime.ofInstant(
                            inputLinkedAccount.getExpires().toInstant(), ZoneId.of("UTC"))
                        + "\"}"));
  }
}

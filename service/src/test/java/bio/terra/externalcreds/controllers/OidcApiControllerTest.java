package bio.terra.externalcreds.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.LinkedAccountService;
import java.sql.Timestamp;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;

import bio.terra.externalcreds.services.SamService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

public class OidcApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockBean private LinkedAccountService linkedAccountService;
  @MockBean private HttpServletRequest httpServletRequest;
  @MockBean private SamService samService;

  @Test
  void testGetLink() throws Exception {
    String userIdFromSam = "";
    String provider = "";
    LinkedAccount inputLinkedAccount =
        LinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .externalUserId("externalUser")
            .build();
    when(httpServletRequest.getHeader(Mockito.anyString())).thenReturn("");
    when(samService.samUsersApi(Mockito.anyString()).getUserStatusInfo().getUserSubjectId())
        .thenReturn("");

    when(linkedAccountService.getLinkedAccount(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(inputLinkedAccount);
    String timestamp = inputLinkedAccount.getExpires().toString();
    String jsonString =
        String.format(
            "{\"externalUserId\":\"externalUser\", \"expirationTimestamp\":\"%s\"}", timestamp);

    mvc.perform(get("/api/oidc/v1/{provider}")).andExpect(content().json(jsonString));
  }
}

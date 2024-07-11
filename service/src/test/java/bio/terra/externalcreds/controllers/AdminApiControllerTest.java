package bio.terra.externalcreds.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.services.LinkedAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminApiControllerTest extends BaseTest {

  @Autowired private ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  @Autowired private ExternalCredsConfig externalCredsConfig;

  @MockBean private LinkedAccountService linkedAccountService;
  @MockBean private ExternalCredsSamUserFactory samUserFactoryMock;

  @Nested
  class GetLinkedAccountForExternalId {

    @Test
    void testGetLinkedAccountForExternalIdAdmin() throws Exception {
      var accessToken = mockAdminSamUser();
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.ERA_COMMONS);

      when(linkedAccountService.getLinkedAccountForExternalId(
              Provider.ERA_COMMONS, inputLinkedAccount.getExternalUserId()))
          .thenReturn(Optional.of(inputLinkedAccount));

      mvc.perform(
              get("/api/admin/"
                      + Provider.ERA_COMMONS
                      + "/v1/userForExternalId/"
                      + inputLinkedAccount.getExternalUserId())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(
              content()
                  .json(
                      mapper.writeValueAsString(
                          OpenApiConverters.Output.convertAdmin(inputLinkedAccount))));
    }

    @Test
    void testGetLinkedAccountForExternalIdNonAdmin() throws Exception {
      var accessToken = mockSamUser("userId");
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.ERA_COMMONS);

      mvc.perform(
              get("/api/admin/"
                      + Provider.ERA_COMMONS
                      + "/v1/userForExternalId/"
                      + inputLinkedAccount.getExternalUserId())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class GetActiveLinkedAccounts {

    @Test
    void testGetActiveLinkedAccountsAdmin() throws Exception {
      var accessToken = mockAdminSamUser();
      var inputLinkedAccount1 = TestUtils.createRandomLinkedAccount(Provider.ERA_COMMONS);
      var inputLinkedAccount2 = TestUtils.createRandomLinkedAccount(Provider.ERA_COMMONS);

      when(linkedAccountService.getActiveLinkedAccounts(Provider.ERA_COMMONS))
          .thenReturn(List.of(inputLinkedAccount1, inputLinkedAccount2));

      mvc.perform(
              get("/api/admin/" + Provider.ERA_COMMONS + "/v1/activeAccounts")
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(
              content()
                  .json(
                      mapper.writeValueAsString(
                          List.of(
                              OpenApiConverters.Output.convertAdmin(inputLinkedAccount1),
                              OpenApiConverters.Output.convertAdmin(inputLinkedAccount2)))));
    }

    @Test
    void testGetActiveLinkedAccountsNonAdmin() throws Exception {
      var accessToken = mockSamUser("userId");

      mvc.perform(
              get("/api/admin/" + Provider.ERA_COMMONS + "/v1/activeAccounts")
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isForbidden());
    }
  }

  private String mockSamUser(String userId) {
    var accessToken = UUID.randomUUID().toString();
    when(samUserFactoryMock.from(any(HttpServletRequest.class)))
        .thenReturn(new SamUser("foo@bar.com", userId, new BearerToken(accessToken)));
    return accessToken;
  }

  private String mockAdminSamUser() {
    var accessToken = UUID.randomUUID().toString();
    when(samUserFactoryMock.from(any(HttpServletRequest.class)))
        .thenReturn(
            new SamUser(
                externalCredsConfig.getAuthorizedAdmins().iterator().next(),
                "userId",
                new BearerToken(accessToken)));
    return accessToken;
  }
}

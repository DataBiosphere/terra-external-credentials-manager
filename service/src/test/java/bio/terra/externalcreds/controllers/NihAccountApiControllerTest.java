package bio.terra.externalcreds.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.services.NihAccountService;
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
class NihAccountApiControllerTest extends BaseTest {

  @Autowired private ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  @Autowired private ExternalCredsConfig externalCredsConfig;

  @MockBean private NihAccountService nihAccountService;
  @MockBean private ExternalCredsSamUserFactory samUserFactoryMock;

  @Nested
  class GetNihAccount {

    @Test
    void testGetNihAccount() throws Exception {
      var inputNihAccount = TestUtils.createRandomNihAccount();
      var accessToken = mockSamUser(inputNihAccount.getUserId());

      when(nihAccountService.getNihAccountForUser(inputNihAccount.getUserId()))
          .thenReturn(Optional.of(inputNihAccount));

      mvc.perform(get("/api/nih/v1/account").header("authorization", "Bearer " + accessToken))
          .andExpect(
              content()
                  .json(
                      mapper.writeValueAsString(
                          OpenApiConverters.Output.convert(inputNihAccount))));
    }

    @Test
    void testGetNihAccountNotFound() throws Exception {
      var inputNihAccount = TestUtils.createRandomNihAccount();
      var accessToken = mockSamUser(inputNihAccount.getUserId());

      when(nihAccountService.getNihAccountForUser(inputNihAccount.getUserId()))
          .thenReturn(Optional.empty());

      mvc.perform(get("/api/nih/v1/account").header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class LinkNihAccount {

    @Test
    void testLinkNihAccountAdmin() throws Exception {
      var accessToken = mockAdminSamUser();
      var inputNihAccount = TestUtils.createRandomNihAccount();

      when(nihAccountService.upsertNihAccount(inputNihAccount))
          .thenReturn(inputNihAccount.withId(1));

      mvc.perform(
              put("/api/nih/v1/account")
                  .header("authorization", "Bearer " + accessToken)
                  .contentType("application/json")
                  .content(
                      mapper.writeValueAsString(OpenApiConverters.Output.convert(inputNihAccount))))
          .andExpect(status().isAccepted());
    }

    @Test
    void testLinkNihAccountNonAdmin() throws Exception {
      var accessToken = mockSamUser("userId");
      var inputNihAccount = TestUtils.createRandomNihAccount();

      mvc.perform(
              put("/api/nih/v1/account")
                  .header("authorization", "Bearer " + accessToken)
                  .contentType("application/json")
                  .content(
                      mapper.writeValueAsString(OpenApiConverters.Output.convert(inputNihAccount))))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class GetNihAccountForUsername {

    @Test
    void testGetNihAccountForUsernameAdmin() throws Exception {
      var accessToken = mockAdminSamUser();
      var inputNihAccount = TestUtils.createRandomNihAccount();

      when(nihAccountService.getLinkedAccountForUsername(inputNihAccount.getNihUsername()))
          .thenReturn(Optional.of(inputNihAccount));

      mvc.perform(
              get("/api/admin/nih/v1/userForUsername/" + inputNihAccount.getNihUsername())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(
              content()
                  .json(
                      mapper.writeValueAsString(
                          OpenApiConverters.Output.convert(inputNihAccount))));
    }

    @Test
    void testGetNihAccountForUsernameNonAdmin() throws Exception {
      var accessToken = mockSamUser("userId");
      var inputNihAccount = TestUtils.createRandomNihAccount();

      mvc.perform(
              get("/api/admin/nih/v1/userForUsername/" + inputNihAccount.getNihUsername())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class GetActiveNihAccounts {

    @Test
    void testGetActiveNihAccountsAdmin() throws Exception {
      var accessToken = mockAdminSamUser();
      var inputNihAccount1 = TestUtils.createRandomNihAccount();
      var inputNihAccount2 = TestUtils.createRandomNihAccount();

      when(nihAccountService.getActiveNihAccounts())
          .thenReturn(List.of(inputNihAccount1, inputNihAccount2));

      mvc.perform(
              get("/api/admin/nih/v1/activeAccounts")
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(
              content()
                  .json(
                      mapper.writeValueAsString(
                          List.of(
                              OpenApiConverters.Output.convert(inputNihAccount1),
                              OpenApiConverters.Output.convert(inputNihAccount2)))));
    }

    @Test
    void testGetActiveNihAccountsNonAdmin() throws Exception {
      var accessToken = mockSamUser("userId");

      mvc.perform(
              get("/api/admin/nih/v1/activeAccounts")
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

package bio.terra.externalcreds.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.SamAdminDAO;
import bio.terra.externalcreds.generated.model.AdminLinkInfo;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.PassportService;
import bio.terra.externalcreds.visaComparators.RASv1Dot1VisaComparator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminApiControllerTest extends BaseTest {

  @Autowired private ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  @Autowired private ExternalCredsConfig externalCredsConfig;

  @MockitoBean private LinkedAccountService linkedAccountService;
  @MockitoBean private PassportService passportService;
  @MockitoBean private ExternalCredsSamUserFactory samUserFactoryMock;
  @MockitoBean private SamAdminDAO samAdminDAO;

  @Nested
  class PutLinkedAccountWithFakeToken {
    @Test
    void testPutLinkedAccountWithFakeTokenAdmin() throws Exception {
      var accessToken = mockAdminSamUser();
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.ERA_COMMONS);
      var inputAdminLinkInfo =
          new AdminLinkInfo()
              .linkedExternalId(inputLinkedAccount.getExternalUserId())
              .linkExpireTime(inputLinkedAccount.getExpires())
              .userId(inputLinkedAccount.getUserId());

      when(linkedAccountService.upsertLinkedAccount(inputLinkedAccount))
          .thenReturn(inputLinkedAccount.withId(1));

      mvc.perform(
              put("/api/admin/v1/" + Provider.ERA_COMMONS)
                  .header("authorization", "Bearer " + accessToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(inputAdminLinkInfo)))
          .andExpect(status().isNoContent());
    }

    @Test
    void testPutLinkedAccountWithFakeTokenAdminNotEraCommons() throws Exception {
      var accessToken = mockAdminSamUser();
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.GITHUB);
      var inputAdminLinkInfo =
          new AdminLinkInfo()
              .linkedExternalId(inputLinkedAccount.getExternalUserId())
              .linkExpireTime(inputLinkedAccount.getExpires())
              .userId(inputLinkedAccount.getUserId());

      mvc.perform(
              put("/api/admin/v1/" + Provider.GITHUB)
                  .header("authorization", "Bearer " + accessToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(inputAdminLinkInfo)))
          .andExpect(status().isForbidden());
    }

    @Test
    void testPutLinkedAccountWithFakeTokenNonAdmin() throws Exception {
      var accessToken = mockSamUser("userId");
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.ERA_COMMONS);
      var inputAdminLinkInfo =
          new AdminLinkInfo()
              .linkedExternalId(inputLinkedAccount.getExternalUserId())
              .linkExpireTime(inputLinkedAccount.getExpires())
              .userId(inputLinkedAccount.getUserId());

      mvc.perform(
              put("/api/admin/v1/" + Provider.ERA_COMMONS)
                  .header("authorization", "Bearer " + accessToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(inputAdminLinkInfo)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class AdminDeleteLinkedAccount {
    @Test
    void testAdminLinkedAccountAdmin() throws Exception {
      var accessToken = mockAdminSamUser();
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.ERA_COMMONS);

      when(linkedAccountService.deleteLinkedAccount(
              inputLinkedAccount.getUserId(), Provider.ERA_COMMONS))
          .thenReturn(true);

      mvc.perform(
              delete("/api/admin/v1/" + Provider.ERA_COMMONS)
                  .header("authorization", "Bearer " + accessToken)
                  .contentType(MediaType.TEXT_PLAIN)
                  .content(inputLinkedAccount.getUserId()))
          .andExpect(status().isNoContent());
    }

    @Test
    void testAdminLinkedAccountAdminNotFound() throws Exception {
      var accessToken = mockAdminSamUser();
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.ERA_COMMONS);

      when(linkedAccountService.deleteLinkedAccount(
              inputLinkedAccount.getUserId(), Provider.ERA_COMMONS))
          .thenReturn(false);

      mvc.perform(
              delete("/api/admin/v1/" + Provider.ERA_COMMONS)
                  .header("authorization", "Bearer " + accessToken)
                  .contentType(MediaType.TEXT_PLAIN)
                  .content(inputLinkedAccount.getUserId()))
          .andExpect(status().isNotFound());
    }

    @Test
    void testPutLinkedAccountWithFakeTokenNonAdmin() throws Exception {
      var accessToken = mockSamUser("userId");
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.ERA_COMMONS);

      mvc.perform(
              delete("/api/admin/v1/" + Provider.ERA_COMMONS)
                  .header("authorization", "Bearer " + accessToken)
                  .contentType(MediaType.TEXT_PLAIN)
                  .content(inputLinkedAccount.getUserId()))
          .andExpect(status().isForbidden());
    }
  }

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
              get("/api/admin/v1/"
                      + Provider.ERA_COMMONS
                      + "/userForExternalId/"
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
              get("/api/admin/v1/"
                      + Provider.ERA_COMMONS
                      + "/userForExternalId/"
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
              get("/api/admin/v1/" + Provider.ERA_COMMONS + "/activeAccounts")
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
              get("/api/admin/v1/" + Provider.ERA_COMMONS + "/activeAccounts")
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class GetVisas {
    @Test
    void testGetVisasAdmin() throws Exception {
      var accessToken = mockAdminSamUser();
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.RAS);
      var issuer = UUID.randomUUID().toString();
      var visaType = UUID.randomUUID().toString();

      List<Map<String, Object>> response = List.of(Map.of("issuer", issuer, "visaType", visaType));
      when(passportService.getVisaClaims(
              Provider.RAS, inputLinkedAccount.getExternalUserId(), issuer, visaType))
          .thenReturn(response);

      mvc.perform(
              get("/api/admin/v1/"
                      + Provider.RAS
                      + "/visas/"
                      + inputLinkedAccount.getExternalUserId())
                  .queryParam("issuer", issuer)
                  .queryParam("visaType", visaType)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isOk())
          .andExpect(content().json(mapper.writeValueAsString(response)));
    }

    @Test
    void testGetVisasNonAdmin() throws Exception {
      var accessToken = mockSamUser("userId");
      var inputLinkedAccount = TestUtils.createRandomLinkedAccount(Provider.RAS);
      var issuer = UUID.randomUUID().toString();
      var visaType = UUID.randomUUID().toString();

      mvc.perform(
              get("/api/admin/v1/"
                      + Provider.RAS
                      + "/visas/"
                      + inputLinkedAccount.getExternalUserId())
                  .queryParam("issuer", issuer)
                  .queryParam("visaType", visaType)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class GetRasSupportInfo {
    @Test
    void testGetRasSupportInfoAdmin() throws Exception {
      var accessToken = mockAdminSamUser();
      var link = TestUtils.createRandomLinkedAccount(Provider.RAS);
      var passport = TestUtils.createRandomPassport();
      var permission =
          new RASv1Dot1VisaComparator.DbGapPermission.Builder()
              .phsId("phs000001")
              .consentGroup("c1")
              .role("pi")
              .build();

      when(linkedAccountService.getLinkedAccount(link.getUserId(), Provider.RAS))
          .thenReturn(Optional.of(link));
      when(passportService.getPassport(link.getUserId(), Provider.RAS))
          .thenReturn(Optional.of(passport));
      when(passportService.getRasDbgapPermissions(link.getUserId()))
          .thenReturn(List.of(permission));

      var expected =
          OpenApiConverters.Output.convertRasSupportInfo(
              link, Optional.of(passport), List.of(permission));

      mvc.perform(
              get("/api/admin/v1/ras/user/" + link.getUserId())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isOk())
          .andExpect(content().json(mapper.writeValueAsString(expected)));
    }

    @Test
    void testGetRasSupportInfoNoPassport() throws Exception {
      var accessToken = mockAdminSamUser();
      var link = TestUtils.createRandomLinkedAccount(Provider.RAS);

      when(linkedAccountService.getLinkedAccount(link.getUserId(), Provider.RAS))
          .thenReturn(Optional.of(link));
      when(passportService.getPassport(link.getUserId(), Provider.RAS))
          .thenReturn(Optional.empty());

      var expected =
          OpenApiConverters.Output.convertRasSupportInfo(link, Optional.empty(), List.of());

      mvc.perform(
              get("/api/admin/v1/ras/user/" + link.getUserId())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isOk())
          .andExpect(content().json(mapper.writeValueAsString(expected)));
    }

    @Test
    void testGetRasSupportInfoNoLink() throws Exception {
      var accessToken = mockAdminSamUser();
      var userId = UUID.randomUUID().toString();

      when(linkedAccountService.getLinkedAccount(userId, Provider.RAS))
          .thenReturn(Optional.empty());

      mvc.perform(
              get("/api/admin/v1/ras/user/" + userId)
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isNotFound());
    }

    @Test
    void testGetRasSupportInfoNonAdmin() throws Exception {
      var accessToken = mockSamUser("userId");

      mvc.perform(
              get("/api/admin/v1/ras/user/someUserId")
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  class RequireAdminSamFallback {
    @Test
    void testSamResourceTypeAdminPermissionAllows() throws Exception {
      var accessToken = mockSamUser("userId");
      var link = TestUtils.createRandomLinkedAccount(Provider.RAS);

      when(samAdminDAO.resourceTypeAdminPermission(
              eq(accessToken), eq("user"), eq("admin_read_summary_information")))
          .thenReturn(true);
      when(linkedAccountService.getLinkedAccount(link.getUserId(), Provider.RAS))
          .thenReturn(Optional.of(link));
      when(passportService.getPassport(link.getUserId(), Provider.RAS))
          .thenReturn(Optional.empty());

      mvc.perform(
              get("/api/admin/v1/ras/user/" + link.getUserId())
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isOk());
    }

    @Test
    void testSamResourceTypeAdminPermissionDenies() throws Exception {
      var accessToken = mockSamUser("userId");

      when(samAdminDAO.resourceTypeAdminPermission(
              eq(accessToken), eq("user"), eq("admin_read_summary_information")))
          .thenReturn(false);

      mvc.perform(
              get("/api/admin/v1/ras/user/someUserId")
                  .header("authorization", "Bearer " + accessToken))
          .andExpect(status().isForbidden());
    }

    @Test
    void testSamResourceTypeAdminPermissionThrows() throws Exception {
      var accessToken = mockSamUser("userId");

      when(samAdminDAO.resourceTypeAdminPermission(
              eq(accessToken), eq("user"), eq("admin_read_summary_information")))
          .thenThrow(new ApiException("Sam unavailable"));

      mvc.perform(
              get("/api/admin/v1/ras/user/someUserId")
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

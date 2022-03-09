package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.JwtSigningTestUtils;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.TokenTypeEnum;
import bio.terra.externalcreds.models.ValidatePassportResult;
import bio.terra.externalcreds.visaComparators.RASv1Dot1Criterion;
import bio.terra.externalcreds.visaComparators.RASv1_1;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

class PassportServiceTest extends BaseTest {
  private static JwtSigningTestUtils jwtSigningTestUtils = new JwtSigningTestUtils();

  @BeforeAll
  static void setUpJwtVerification() throws JOSEException {
    jwtSigningTestUtils.setUpJwtVerification();
  }

  @AfterAll
  static void tearDown() {
    jwtSigningTestUtils.tearDown();
  }

  @Nested
  @TestComponent
  class GetGA4GHPassport {
    @Autowired PassportService passportService;
    @Autowired LinkedAccountDAO linkedAccountDAO;
    @Autowired GA4GHPassportDAO passportDAO;

    @Test
    void testGetGA4GHPassport() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();

      var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
      var savedPassport =
          passportDAO.insertPassport(passport.withLinkedAccountId(savedLinkedAccount.getId()));

      var loadedPassport =
          passportService.getPassport(linkedAccount.getUserId(), linkedAccount.getProviderName());

      assertPresent(loadedPassport);
      assertEquals(passport.getJwt(), savedPassport.getJwt());
      assertEquals(passport.getExpires(), savedPassport.getExpires());
    }

    @Test
    void testGetGA4GHPassportNoLinkedAccount() {
      var userId = "nonexistent_user_id";
      var providerName = "fake_provider";

      assertEmpty(passportService.getPassport(userId, providerName));
    }

    @Test
    void testGetGA4GHPassportLinkedAccountNoPassport() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      linkedAccountDAO.upsertLinkedAccount(linkedAccount);

      assertEmpty(
          passportService.getPassport(linkedAccount.getUserId(), linkedAccount.getProviderName()));
    }
  }

  @Nested
  @TestComponent
  class ValidatePassport {
    @Autowired PassportService passportService;
    @Autowired LinkedAccountService linkedAccountService;
    @Autowired ObjectMapper objectMapper;

    @MockBean ExternalCredsConfig externalCredsConfigMock;
    @MockBean ProviderClientCache providerClientCacheMock;

    @Test
    void testValidPassportMatchingCriteria() throws URISyntaxException, JsonProcessingException {
      var linkedAccount = TestUtils.createRandomLinkedAccount();

      var providerInfo = TestUtils.createRandomProvider();
      var providerClient =
          ClientRegistration.withRegistrationId(linkedAccount.getProviderName())
              .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
              .build();

      when(externalCredsConfigMock.getProviders())
          .thenReturn(Map.of(linkedAccount.getProviderName(), providerInfo));
      when(externalCredsConfigMock.getAllowedJwtIssuers())
          .thenReturn(List.of(new URI(jwtSigningTestUtils.getIssuer())));
      when(externalCredsConfigMock.getAllowedJwksUris())
          .thenReturn(
              List.of(new URI(jwtSigningTestUtils.getIssuer() + JwtSigningTestUtils.JKU_PATH)));
      when(providerClientCacheMock.getProviderClient(linkedAccount.getProviderName()))
          .thenReturn(Optional.of(providerClient));

      var matchingConsentGroup = "c33";
      var matchingPhsId = "phs987";

      var visaNoMatch =
          jwtSigningTestUtils.createTestVisaWithJwtWithClaims(
              TokenTypeEnum.access_token,
              Map.of(
                  RASv1_1.DBGAP_CLAIM,
                  Set.of(
                      Map.of(
                          "phs_id",
                          "phs789",
                          "consent_group",
                          matchingConsentGroup,
                          "role",
                          "bar"))));
      var visaYesMatch =
          jwtSigningTestUtils.createTestVisaWithJwtWithClaims(
              TokenTypeEnum.access_token,
              Map.of(
                  RASv1_1.DBGAP_CLAIM,
                  Set.of(
                      Map.of(
                          "phs_id",
                          matchingPhsId,
                          "consent_group",
                          matchingConsentGroup,
                          "role",
                          "bar"))));

      var visas = List.of(visaYesMatch, visaNoMatch);
      var matchingPassport =
          jwtSigningTestUtils.createTestPassport(visas, linkedAccount.getExternalUserId());

      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount)
              .passport(matchingPassport)
              .visas(visas)
              .build());

      // more than one criterion
      // one criterion matches one passport
      var criterion =
          new RASv1Dot1Criterion.Builder()
              .phsId(matchingPhsId)
              .consentCode(matchingConsentGroup)
              .issuer(jwtSigningTestUtils.getIssuer())
              .build();
      var result =
          passportService.validatePassport(List.of(matchingPassport.getJwt()), List.of(criterion));

      assertEquals(
          new ValidatePassportResult.Builder()
              .valid(true)
              .auditInfo(
                  Map.of(
                      "passport_jti",
                      matchingPassport.getJwtId(),
                      "external_user_id",
                      linkedAccount.getExternalUserId(),
                      "internal_user_id",
                      linkedAccount.getUserId()))
              .matchedCriterion(criterion)
              .build(),
          result);
    }

    @Test
    void testValidPassportNotMatchingCriteria() {}

    @Test
    void testInvalidPassport() {
      // one valid passport with matching criteria, one invalid passport
    }

    @Test
    void testPassportsWithDifferentUsers() {
      // 2 passports but each from a different linked account
    }

    @Test
    void testPassportsWithoutUser() {}
  }
}

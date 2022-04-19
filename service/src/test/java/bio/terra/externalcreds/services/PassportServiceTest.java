package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.JwtSigningTestUtils;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.TokenTypeEnum;
import bio.terra.externalcreds.models.ValidatePassportResultInternal;
import bio.terra.externalcreds.visaComparators.RASv1Dot1VisaComparator;
import bio.terra.externalcreds.visaComparators.RASv1Dot1VisaComparator.DbGapPermission;
import bio.terra.externalcreds.visaComparators.RASv1Dot1VisaCriterionInternal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private static final JwtSigningTestUtils jwtSigningTestUtils = new JwtSigningTestUtils();

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
    void testValidPassportMatchingCriteria() throws URISyntaxException {
      runValidPassportTest(new ValidPassportTestParams());
    }

    @Test
    void testValidPassportNotMatchingIssuer() throws URISyntaxException {
      var params = new ValidPassportTestParams();
      params.valid = false;
      params.issuer = "https://some.other.issuer";
      runValidPassportTest(params);
    }

    @Test
    void testValidPassportNotMatchingVisaType() throws URISyntaxException {
      var params = new ValidPassportTestParams();
      params.valid = false;
      params.visaType = "wrong visa type";
      runValidPassportTest(params);
    }

    @Test
    void testValidPassportNotMatchingCriteria() throws URISyntaxException {
      var params = new ValidPassportTestParams();
      params.valid = false;
      params.criterionPhsId = "phsDIFFERENT";
      runValidPassportTest(params);
    }

    @Test
    void testTransactionIdAppearsInAuditInfo() throws URISyntaxException {
      var params = new ValidPassportTestParams();
      var transactionClaim = Map.of("txn", "testTransactionId");
      params.customJwtClaims = transactionClaim;
      params.customAuditInfoExpected = transactionClaim;
      runValidPassportTest(params);
    }

    @Test
    void testValidPassportWithoutUserThrows() {
      var params = new ValidPassportTestParams();
      params.persistLinkedAccount = false;
      assertThrows(BadRequestException.class, () -> runValidPassportTest(params));
    }

    @Test
    void testInvalidPassportThrows() {
      var criterion =
          new RASv1Dot1VisaCriterionInternal.Builder().phsId("").consentCode("").issuer("").build();

      assertThrows(
          BadRequestException.class,
          () -> passportService.validatePassport(List.of("garbage"), List.of(criterion)));
    }

    @Test
    void testPassportsWithDifferentUsersThrows() throws URISyntaxException {
      var linkedAccount1 = TestUtils.createRandomLinkedAccount();
      var linkedAccount2 = linkedAccount1.withExternalUserId(UUID.randomUUID().toString());
      mockProviderConfig(linkedAccount1);

      var passport1 = jwtSigningTestUtils.createTestPassport(List.of());
      var passport2 = jwtSigningTestUtils.createTestPassport(List.of());

      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount1)
              .passport(passport1)
              .build());
      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount2)
              .passport(passport2)
              .build());

      var criterion =
          new RASv1Dot1VisaCriterionInternal.Builder().phsId("").consentCode("").issuer("").build();

      assertThrows(
          BadRequestException.class,
          () -> passportService.validatePassport(List.of(passport1.getJwt()), List.of(criterion)));
    }

    /**
     * Parameters used to vary how runValidPassportTest works. Default values represent the golden
     * path.
     */
    class ValidPassportTestParams {
      boolean valid = true;
      String issuer = jwtSigningTestUtils.getIssuer();
      String visaType = RASv1Dot1VisaComparator.RAS_VISAS_V_1_1;
      String visaPhsId = "phs000123";
      String criterionPhsId = visaPhsId;
      boolean persistLinkedAccount = true;
      Map<String, String> customJwtClaims = Collections.emptyMap();
      Map<String, String> customAuditInfoExpected = Collections.emptyMap();
    }

    private void runValidPassportTest(ValidPassportTestParams params) throws URISyntaxException {
      when(externalCredsConfigMock.getAllowedJwtAlgorithms()).thenReturn(List.of("RS256", "ES256"));

      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var otherLinkedAccount =
          TestUtils.createRandomLinkedAccount().withUserId(linkedAccount.getUserId());
      mockProviderConfig(linkedAccount, otherLinkedAccount);

      var matchingPermission =
          new DbGapPermission.Builder()
              .phsId(params.visaPhsId)
              .consentGroup("c33")
              .role("bar")
              .build();
      var notMatchingPermission = matchingPermission.withPhsId("something_else");

      var visaWithMatchingPermission = createDbGapVisa(Set.of(matchingPermission), params.visaType);
      var visaWithoutMatchingPermission =
          createDbGapVisa(Set.of(notMatchingPermission), params.visaType);

      var visas = List.of(visaWithMatchingPermission);
      var passport = jwtSigningTestUtils.createTestPassport(visas, params.customJwtClaims);

      var otherVisas = List.of(visaWithoutMatchingPermission);
      var otherPassport =
          jwtSigningTestUtils.createTestPassport(otherVisas, params.customJwtClaims);

      if (params.persistLinkedAccount) {
        linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
            new LinkedAccountWithPassportAndVisas.Builder()
                .linkedAccount(linkedAccount)
                .passport(passport)
                .visas(visas)
                .build());
        linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
            new LinkedAccountWithPassportAndVisas.Builder()
                .linkedAccount(otherLinkedAccount)
                .passport(otherPassport)
                .visas(otherVisas)
                .build());
      }

      var criterion =
          new RASv1Dot1VisaCriterionInternal.Builder()
              .phsId(params.criterionPhsId)
              .consentCode(matchingPermission.getConsentGroup())
              .issuer(params.issuer)
              .build();

      var result =
          passportService.validatePassport(
              List.of(otherPassport.getJwt(), passport.getJwt()), List.of(criterion));

      if (params.valid) {
        assertEquals(
            new ValidatePassportResultInternal.Builder()
                .valid(true)
                .auditInfo(
                    expectedAuditInfo(linkedAccount, passport, params.customAuditInfoExpected))
                .matchedCriterion(criterion)
                .build(),
            result);
      } else {
        assertEquals(
            new ValidatePassportResultInternal.Builder()
                .valid(false)
                .auditInfo(expectedAuditInfo(linkedAccount))
                .build(),
            result);
      }
    }

    private Map<String, String> expectedAuditInfo(
        LinkedAccount linkedAccount,
        GA4GHPassport passport,
        Map<String, String> customInfoExpected) {
      var expectedAuditInfo =
          new HashMap<>(
              Map.of(
                  "passport_jti",
                  passport.getJwtId(),
                  "external_user_id",
                  linkedAccount.getExternalUserId(),
                  "internal_user_id",
                  linkedAccount.getUserId()));
      expectedAuditInfo.putAll(customInfoExpected);
      return expectedAuditInfo;
    }

    private Map<String, String> expectedAuditInfo(LinkedAccount linkedAccount) {
      return Map.of("internal_user_id", linkedAccount.getUserId());
    }

    private void mockProviderConfig(LinkedAccount... linkedAccounts) throws URISyntaxException {
      var providerInfos =
          Arrays.stream(linkedAccounts)
              .map(
                  linkedAccount ->
                      Map.of(linkedAccount.getProviderName(), TestUtils.createRandomProvider()))
              .reduce(
                  new HashMap<>(),
                  (a, b) -> {
                    a.putAll(b);
                    return a;
                  });

      when(externalCredsConfigMock.getProviders()).thenReturn(providerInfos);
      when(externalCredsConfigMock.getAllowedJwtIssuers())
          .thenReturn(List.of(new URI(jwtSigningTestUtils.getIssuer())));
      when(externalCredsConfigMock.getAllowedJwksUris())
          .thenReturn(
              List.of(new URI(jwtSigningTestUtils.getIssuer() + JwtSigningTestUtils.JKU_PATH)));

      Arrays.stream(linkedAccounts)
          .forEach(
              linkedAccount -> {
                var providerClient =
                    ClientRegistration.withRegistrationId(linkedAccount.getProviderName())
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .build();
                when(providerClientCacheMock.getProviderClient(linkedAccount.getProviderName()))
                    .thenReturn(Optional.of(providerClient));
              });
    }

    private GA4GHVisa createDbGapVisa(Set<DbGapPermission> permissions) {
      return createDbGapVisa(permissions, RASv1Dot1VisaComparator.RAS_VISAS_V_1_1);
    }

    private GA4GHVisa createDbGapVisa(Set<DbGapPermission> permissions, String visaType) {
      // we spend too much time trying to figure out how to get the innards of the JWT building
      // to serialize DbGapPermission as the right json - instead just make it a Map
      var permissionsAsMaps =
          permissions.stream()
              .map(
                  p ->
                      Map.of(
                          "phs_id",
                          p.getPhsId(),
                          "consent_group",
                          p.getConsentGroup(),
                          "role",
                          p.getRole()))
              .collect(Collectors.toSet());
      return jwtSigningTestUtils.createTestVisaWithJwtWithClaims(
          TokenTypeEnum.access_token,
          Map.of(RASv1Dot1VisaComparator.DBGAP_CLAIM, permissionsAsMaps),
          visaType);
    }
  }
}

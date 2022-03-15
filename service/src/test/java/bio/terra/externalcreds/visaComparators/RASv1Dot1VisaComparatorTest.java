package bio.terra.externalcreds.visaComparators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.JwtSigningTestUtils;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.ImmutableGA4GHVisa;
import bio.terra.externalcreds.models.TokenTypeEnum;
import bio.terra.externalcreds.services.JwtUtils;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RASv1Dot1VisaComparatorTest extends BaseTest {
  private static final JwtSigningTestUtils jwtSigningTestUtils = new JwtSigningTestUtils();

  @Autowired private RASv1Dot1VisaComparator comparator;

  @BeforeAll
  static void setUpJwtVerification() throws JOSEException {
    jwtSigningTestUtils.setUpJwtVerification();
  }

  @AfterAll
  static void tearDown() {
    jwtSigningTestUtils.tearDown();
  }

  @Test
  void testSameJwt() {
    var visa =
        createTestRasVisa(Map.of("phs_id", "phs000021", "consent_group", "c1", "role", "pi"));

    assertTrue(comparator.authorizationsMatch(visa, visa));
  }

  @Test
  void testSameAuthorizationsDifferentOrder() {
    var authorization1 = Map.of("phs_id", "phs000021", "consent_group", "c1", "role", "pi");
    var authorization2 = Map.of("phs_id", "phs000022", "consent_group", "c1", "role", "pi");

    assertTrue(
        comparator.authorizationsMatch(
            createTestRasVisa(authorization1, authorization2),
            createTestRasVisa(authorization2, authorization1)));
  }

  @Test
  void testSameAuthorizationWithExtraInfo() {
    var authorization1 =
        Map.of("phs_id", "phs000021", "consent_group", "c1", "role", "pi", "unimportant", "a");
    var authorization2 =
        Map.of(
            "phs_id",
            "phs000021",
            "consent_group",
            "c1",
            "role",
            "pi",
            "unimportant",
            "b",
            "extra",
            "foo");

    assertTrue(
        comparator.authorizationsMatch(
            createTestRasVisa(authorization1), createTestRasVisa(authorization2)));
  }

  @Test
  void testDifferentAuthorization() {
    var authorization1 = Map.of("phs_id", "phs000021", "consent_group", "c1", "role", "pi");
    var authorization2 = Map.of("phs_id", "phs000022", "consent_group", "c1", "role", "pi");

    assertFalse(
        comparator.authorizationsMatch(
            createTestRasVisa(authorization1), createTestRasVisa(authorization2)));
  }

  @Test
  void testDifferentVisaTypes() {
    var visa = createTestRasVisa(Map.of("phs_id", "phs000021", "consent_group", "c1"));

    assertFalse(comparator.authorizationsMatch(visa, visa.withVisaType("different")));
  }

  @Test
  void testUnsupportedVisaType() {
    var visa =
        createTestRasVisa(Map.of("phs_id", "phs000021", "consent_group", "c1"))
            .withVisaType("unsupported");

    assertFalse(comparator.visaTypeSupported(visa));
  }

  @Test
  void testDuplicateAuthorizations() {
    var authorization = Map.of("phs_id", "phs000021", "consent_group", "c1", "role", "pi");

    assertTrue(
        comparator.authorizationsMatch(
            createTestRasVisa(authorization, authorization), createTestRasVisa(authorization)));
  }

  @SafeVarargs
  private ImmutableGA4GHVisa createTestRasVisa(Map<String, String>... dbgapPermissions) {
    return new GA4GHVisa.Builder()
        .jwt(createVisaJwtString(dbgapPermissions))
        .visaType(RASv1Dot1VisaComparator.RAS_VISAS_V_1_1)
        .tokenType(TokenTypeEnum.access_token)
        .expires(new Timestamp(0))
        .issuer("https://stsstg.nih.gov")
        .build();
  }

  @SafeVarargs
  private String createVisaJwtString(Map<String, String>... dbgapPermissions) {
    var visaClaimSet =
        new JWTClaimsSet.Builder()
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .issuer("https://stsstg.nih.gov")
            .claim(
                JwtUtils.GA4GH_VISA_V1_CLAIM,
                Map.of(JwtUtils.VISA_TYPE_CLAIM, RASv1Dot1VisaComparator.RAS_VISAS_V_1_1))
            .claim(RASv1Dot1VisaComparator.DBGAP_CLAIM, dbgapPermissions)
            .build();

    return jwtSigningTestUtils.createSignedJwt(visaClaimSet);
  }
}

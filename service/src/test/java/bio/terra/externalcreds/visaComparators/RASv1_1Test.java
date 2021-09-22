package bio.terra.externalcreds.visaComparators;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.JwtSigningTestUtils;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.ImmutableGA4GHVisa;
import bio.terra.externalcreds.models.TokenTypeEnum;
import bio.terra.externalcreds.services.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader.Builder;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RASv1_1Test extends BaseTest {
  private static JwtSigningTestUtils jwtSigningTestUtils = new JwtSigningTestUtils();

  @Autowired private ObjectMapper objectMapper;

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
    var comparator = new RASv1_1(objectMapper);

    var visa = createTestRasVisa(Map.of("phs_id", "phs000021", "consent_group", "c1"));

    assertTrue(comparator.authorizationsMatch(visa, visa));
  }

  @Test
  void testSameAuthorizationsDifferentOrder() {
    var comparator = new RASv1_1(objectMapper);
    var authorization1 = Map.of("phs_id", "phs000021", "consent_group", "c1");
    var authorization2 = Map.of("phs_id", "phs000022", "consent_group", "c1");

    assertTrue(
        comparator.authorizationsMatch(
            createTestRasVisa(authorization1, authorization2),
            createTestRasVisa(authorization2, authorization1)));
  }

  @Test
  void testSameAuthorizationWithExtraInfo() {
    var comparator = new RASv1_1(objectMapper);
    var authorization1 = Map.of("phs_id", "phs000021", "consent_group", "c1");
    var authorization2 = Map.of("phs_id", "phs000021", "consent_group", "c1", "extra", "foo");

    assertTrue(
        comparator.authorizationsMatch(
            createTestRasVisa(authorization1), createTestRasVisa(authorization2)));
  }

  @Test
  void testDifferentAuthorization() {
    var comparator = new RASv1_1(objectMapper);
    var authorization1 = Map.of("phs_id", "phs000021", "consent_group", "c1");
    var authorization2 = Map.of("phs_id", "phs000022", "consent_group", "c1");

    assertFalse(
        comparator.authorizationsMatch(
            createTestRasVisa(authorization1), createTestRasVisa(authorization2)));
  }

  @Test
  void testDifferentVisaTypes() {
    var comparator = new RASv1_1(objectMapper);

    var visa = createTestRasVisa(Map.of("phs_id", "phs000021", "consent_group", "c1"));

    assertFalse(comparator.authorizationsMatch(visa, visa.withVisaType("different")));
  }

  @Test
  void testUnsupportedVisaType() {
    var comparator = new RASv1_1(objectMapper);

    var visa =
        createTestRasVisa(Map.of("phs_id", "phs000021", "consent_group", "c1"))
            .withVisaType("unsupported");

    assertFalse(comparator.visaTypeSupported(visa));
  }

  private ImmutableGA4GHVisa createTestRasVisa(Map<String, String>... dbgapPermissions) {
    return new GA4GHVisa.Builder()
        .jwt(createVisaJwtString(dbgapPermissions))
        .visaType(RASv1_1.RAS_VISAS_V_1_1)
        .tokenType(TokenTypeEnum.access_token)
        .expires(new Timestamp(0))
        .issuer("https://stsstg.nih.gov")
        .build();
  }

  @SneakyThrows
  private String createVisaJwtString(Map<String, String>... dbgapPermissions) {
    var visaClaimSet =
        new JWTClaimsSet.Builder()
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .issuer("https://stsstg.nih.gov")
            .claim(
                JwtUtils.GA4GH_VISA_V1_CLAIM,
                Map.of(JwtUtils.VISA_TYPE_CLAIM, RASv1_1.RAS_VISAS_V_1_1))
            .claim(RASv1_1.DBGAP_CLAIM, dbgapPermissions)
            .build();

    var jwtHeaderBuilder = new Builder(JWSAlgorithm.RS256);

    jwtHeaderBuilder.keyID(jwtSigningTestUtils.accessTokenRsaJWK.getKeyID());
    var signedVisaJwt = new SignedJWT(jwtHeaderBuilder.build(), visaClaimSet);
    signedVisaJwt.sign(jwtSigningTestUtils.accessTokenSigner);
    return signedVisaJwt.serialize();
  }
}

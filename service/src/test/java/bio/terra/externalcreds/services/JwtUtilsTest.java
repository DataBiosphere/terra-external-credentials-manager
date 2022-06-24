package bio.terra.externalcreds.services;

import static bio.terra.externalcreds.TestUtils.getRootCause;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.JwtSigningTestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.models.TokenTypeEnum;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWTClaimsSet;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;

public class JwtUtilsTest extends BaseTest {

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
  class DecodeJwt {
    @Autowired JwtUtils jwtUtils;

    @MockBean ExternalCredsConfig externalCredsConfigMock;

    @Test
    void testInvalidJwtSignature() {
      var visa = jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.access_token);
      var invalidJwt = visa.getJwt() + "foo";
      assertThrows(InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(invalidJwt));
    }

    @Test
    void testJwtMissingIssuer() {
      var visa =
          jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.access_token).withIssuer("null");
      var jwtMissingIssuer = jwtSigningTestUtils.createVisaJwtString(visa);
      assertThrows(
          InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwtMissingIssuer));
    }

    @Test
    void testJwtIssuerAllowed() throws URISyntaxException {
      when(externalCredsConfigMock.getAllowedJwtAlgorithms()).thenReturn(List.of("RS256", "ES256"));
      when(externalCredsConfigMock.getAllowedJwtIssuers())
          .thenReturn(List.of(new URI(jwtSigningTestUtils.getIssuer())));
      var jwt =
          jwtSigningTestUtils.createVisaJwtString(
              jwtSigningTestUtils
                  .createTestVisaWithJwt(TokenTypeEnum.access_token)
                  .withIssuer(jwtSigningTestUtils.getIssuer()));
      assertNotNull(jwtUtils.decodeAndValidateJwt(jwt));
    }

    @Test
    void testJwtIssuerNotAllowed() {
      var jwt =
          jwtSigningTestUtils.createVisaJwtString(
              jwtSigningTestUtils
                  .createTestVisaWithJwt(TokenTypeEnum.access_token)
                  .withIssuer(jwtSigningTestUtils.getIssuer()));
      assertThrows(InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwt));
    }

    @Test
    void testJwtIssuerNotReal() throws URISyntaxException {
      String testIssuer = "http://does.not.exist";
      when(externalCredsConfigMock.getAllowedJwtAlgorithms()).thenReturn(List.of("RS256", "ES256"));
      when(externalCredsConfigMock.getAllowedJwtIssuers()).thenReturn(List.of(new URI(testIssuer)));
      var jwtNotRealIssuer =
          jwtSigningTestUtils.createVisaJwtString(
              jwtSigningTestUtils
                  .createTestVisaWithJwt(TokenTypeEnum.access_token)
                  .withIssuer(testIssuer));
      var e =
          assertThrows(
              InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwtNotRealIssuer));

      assertInstanceOf(UnknownHostException.class, getRootCause(e));
    }

    @Test
    void testJwtJkuNotOnAllowList() {
      var badJwt = jwtSigningTestUtils.createTestVisaWithJwt(TokenTypeEnum.document_token).getJwt();
      var exception =
          assertThrows(InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(badJwt));

      assertTrue(exception.getMessage().contains("not on allowed list"));
    }

    @Test
    void testJwtAlgNotOnAllowList() throws URISyntaxException {
      var issuer = "https://stsstg.nih.gov";
      when(externalCredsConfigMock.getAllowedJwtIssuers()).thenReturn(List.of(new URI(issuer)));

      // These are explicitly different from the RS256 algo
      // used to create the signed test JWT
      when(externalCredsConfigMock.getAllowedJwtAlgorithms())
          .thenReturn(List.of("EdDSA", "ES256K"));

      var visaClaimSet =
          new JWTClaimsSet.Builder()
              .expirationTime(new Date(System.currentTimeMillis()))
              .issuer(issuer)
              .claim(
                  JwtUtils.GA4GH_VISA_V1_CLAIM,
                  Map.of(JwtUtils.VISA_TYPE_CLAIM, TokenTypeEnum.document_token))
              .build();

      var jwt = jwtSigningTestUtils.createSignedJwt(visaClaimSet);
      var exception =
          assertThrows(InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwt));

      assertTrue(exception.getMessage().contains("Algorithm"));
    }

    @Test
    void testJwtJkuNotResponsive() throws URISyntaxException {
      String testIssuer = "http://localhost:10";
      when(externalCredsConfigMock.getAllowedJwtAlgorithms()).thenReturn(List.of("RS256", "ES256"));
      when(externalCredsConfigMock.getAllowedJwtIssuers()).thenReturn(List.of(new URI(testIssuer)));
      when(externalCredsConfigMock.getAllowedJwksUris())
          .thenReturn(List.of(new URI(testIssuer + JwtSigningTestUtils.JKU_PATH)));

      var jwtNotResponsiveJku =
          jwtSigningTestUtils.createVisaJwtString(
              jwtSigningTestUtils
                  .createTestVisaWithJwt(TokenTypeEnum.document_token)
                  .withIssuer(testIssuer));

      var exception =
          assertThrows(
              InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwtNotResponsiveJku));
      assertTrue(exception.getMessage().contains("Connection refused"));
    }

    @Test
    void testJwtJkuMalformed() throws URISyntaxException {
      String testIssuer = "foobar";
      when(externalCredsConfigMock.getAllowedJwtAlgorithms()).thenReturn(List.of("RS256", "ES256"));
      when(externalCredsConfigMock.getAllowedJwtIssuers()).thenReturn(List.of(new URI(testIssuer)));
      when(externalCredsConfigMock.getAllowedJwksUris())
          .thenReturn(List.of(new URI(testIssuer + JwtSigningTestUtils.JKU_PATH)));

      var jwtMalformedJku =
          jwtSigningTestUtils.createVisaJwtString(
              jwtSigningTestUtils
                  .createTestVisaWithJwt(TokenTypeEnum.document_token)
                  .withIssuer(testIssuer));

      var exception =
          assertThrows(
              InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwtMalformedJku));
      assertTrue(exception.getMessage().contains("URI is not absolute"));
    }
  }
}

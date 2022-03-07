package bio.terra.externalcreds.services;

import static bio.terra.externalcreds.TestUtils.getRootCause;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.JwtSigningTestUtils;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.models.TokenTypeEnum;
import com.nimbusds.jose.JOSEException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
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
    void testInvalidJwtSignature() throws URISyntaxException, JOSEException {
      var visa =
          jwtSigningTestUtils.createTestVisaWithJwt(
              TokenTypeEnum.access_token, TestUtils.getRandomTimestamp());
      var invalidJwt = visa.getJwt() + "foo";
      assertThrows(InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(invalidJwt));
    }

    @Test
    void testJwtMissingIssuer() throws URISyntaxException, JOSEException {
      var visa =
          jwtSigningTestUtils
              .createTestVisaWithJwt(TokenTypeEnum.access_token, TestUtils.getRandomTimestamp())
              .withIssuer("null");
      var jwtMissingIssuer = jwtSigningTestUtils.createVisaJwtString(visa);
      assertThrows(
          InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwtMissingIssuer));
    }

    @Test
    void testJwtIssuerAllowed() throws URISyntaxException, JOSEException {
      when(externalCredsConfigMock.getAllowedJwtIssuers())
          .thenReturn(List.of(new URI(jwtSigningTestUtils.getIssuer())));
      var jwt =
          jwtSigningTestUtils.createVisaJwtString(
              jwtSigningTestUtils
                  .createTestVisaWithJwt(TokenTypeEnum.access_token, TestUtils.getRandomTimestamp())
                  .withIssuer(jwtSigningTestUtils.getIssuer()));
      assertNotNull(jwtUtils.decodeAndValidateJwt(jwt));
    }

    @Test
    void testJwtIssuerNotAllowed() throws URISyntaxException, JOSEException {
      var jwt =
          jwtSigningTestUtils.createVisaJwtString(
              jwtSigningTestUtils
                  .createTestVisaWithJwt(TokenTypeEnum.access_token, TestUtils.getRandomTimestamp())
                  .withIssuer(jwtSigningTestUtils.getIssuer()));
      assertThrows(InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwt));
    }

    @Test
    void testJwtIssuerNotReal() throws URISyntaxException, JOSEException {
      String testIssuer = "http://does.not.exist";
      when(externalCredsConfigMock.getAllowedJwtIssuers()).thenReturn(List.of(new URI(testIssuer)));
      var jwtNotRealIssuer =
          jwtSigningTestUtils.createVisaJwtString(
              jwtSigningTestUtils
                  .createTestVisaWithJwt(TokenTypeEnum.access_token, TestUtils.getRandomTimestamp())
                  .withIssuer(testIssuer));
      var e =
          assertThrows(
              InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwtNotRealIssuer));

      assertInstanceOf(UnknownHostException.class, getRootCause(e));
    }

    @Test
    void testJwtJkuNotOnAllowList() throws URISyntaxException, JOSEException {
      var badJwt =
          jwtSigningTestUtils
              .createTestVisaWithJwt(TokenTypeEnum.document_token, TestUtils.getRandomTimestamp())
              .getJwt();
      var exception =
          assertThrows(InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(badJwt));

      assertTrue(exception.getMessage().contains("not on allowed list"));
    }

    @Test
    void testJwtJkuNotResponsive() throws URISyntaxException, JOSEException {
      String testIssuer = "http://localhost:10";
      when(externalCredsConfigMock.getAllowedJwtIssuers()).thenReturn(List.of(new URI(testIssuer)));
      when(externalCredsConfigMock.getAllowedJwksUris())
          .thenReturn(List.of(new URI(testIssuer + JwtSigningTestUtils.JKU_PATH)));

      var jwtNotResponsiveJku =
          jwtSigningTestUtils.createVisaJwtString(
              jwtSigningTestUtils
                  .createTestVisaWithJwt(
                      TokenTypeEnum.document_token, TestUtils.getRandomTimestamp())
                  .withIssuer(testIssuer));

      var exception =
          assertThrows(
              InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwtNotResponsiveJku));
      assertTrue(exception.getMessage().contains("Connection refused"));
    }

    @Test
    void testJwtJkuMalformed() throws URISyntaxException, JOSEException {
      String testIssuer = "foobar";
      when(externalCredsConfigMock.getAllowedJwtIssuers()).thenReturn(List.of(new URI(testIssuer)));
      when(externalCredsConfigMock.getAllowedJwksUris())
          .thenReturn(List.of(new URI(testIssuer + JwtSigningTestUtils.JKU_PATH)));

      var jwtMalformedJku =
          jwtSigningTestUtils.createVisaJwtString(
              jwtSigningTestUtils
                  .createTestVisaWithJwt(
                      TokenTypeEnum.document_token, TestUtils.getRandomTimestamp())
                  .withIssuer(testIssuer));

      var exception =
          assertThrows(
              InvalidJwtException.class, () -> jwtUtils.decodeAndValidateJwt(jwtMalformedJku));
      assertTrue(exception.getMessage().contains("URI is not absolute"));
    }
  }
}

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

class LinkedIdentitiesVisaComparatorTest extends BaseTest {
  private static final JwtSigningTestUtils jwtSigningTestUtils = new JwtSigningTestUtils();
  private static final String SOURCE = "https://stsstg.nih.gov";
  private static final String BY = "nih.gov";

  @Autowired private LinkedIdentitiesVisaComparator comparator;

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
    var visa = createTestLinkedIdentitiesVisa("value1", SOURCE, 1749660063L);

    assertTrue(comparator.authorizationsMatch(visa, visa));
  }

  @Test
  void testSameContentDifferentAsserted() {
    var visa1 = createTestLinkedIdentitiesVisa("value1", SOURCE, 1749660063L);
    var visa2 = createTestLinkedIdentitiesVisa("value1", SOURCE, 1849660063L);

    assertTrue(comparator.authorizationsMatch(visa1, visa2));
  }

  @Test
  void testDifferentValue() {
    var visa1 = createTestLinkedIdentitiesVisa("value1", SOURCE, 1749660063L);
    var visa2 = createTestLinkedIdentitiesVisa("value2", SOURCE, 1749660063L);

    assertFalse(comparator.authorizationsMatch(visa1, visa2));
  }

  @Test
  void testDifferentSource() {
    var visa1 = createTestLinkedIdentitiesVisa("value1", SOURCE, 1749660063L);
    var visa2 =
        createTestLinkedIdentitiesVisa("value1", "https://different-source.org", 1749660063L);

    assertFalse(comparator.authorizationsMatch(visa1, visa2));
  }

  @Test
  void testDifferentVisaTypes() {
    var visa = createTestLinkedIdentitiesVisa("value1", SOURCE, 1749660063L);

    assertFalse(comparator.authorizationsMatch(visa, visa.withVisaType("different")));
  }

  @Test
  void testUnsupportedVisaType() {
    var visa =
        createTestLinkedIdentitiesVisa("value1", SOURCE, 1749660063L).withVisaType("unsupported");

    assertFalse(comparator.visaTypeSupported(visa));
  }

  @Test
  void testSupportedVisaType() {
    var visa = createTestLinkedIdentitiesVisa("value1", SOURCE, 1749660063L);

    assertTrue(comparator.visaTypeSupported(visa));
  }

  private ImmutableGA4GHVisa createTestLinkedIdentitiesVisa(
      String value, String source, Long asserted) {
    return new GA4GHVisa.Builder()
        .jwt(createLinkedIdentitiesJwtString(value, source, asserted))
        .visaType(LinkedIdentitiesVisaComparator.LINKED_IDENTITIES_VISA_TYPE)
        .tokenType(TokenTypeEnum.access_token)
        .expires(new Timestamp(System.currentTimeMillis() + 60000))
        .issuer(source)
        .build();
  }

  private String createLinkedIdentitiesJwtString(String value, String source, Long asserted) {
    var visaData =
        Map.of(
            "type", LinkedIdentitiesVisaComparator.LINKED_IDENTITIES_VISA_TYPE,
            "asserted", asserted,
            "value", value,
            "source", source);

    var visaClaimSet =
        new JWTClaimsSet.Builder()
            .expirationTime(new Date(System.currentTimeMillis() + 60000))
            .issuer(source)
            .claim(JwtUtils.GA4GH_VISA_V1_CLAIM, visaData)
            .build();

    return jwtSigningTestUtils.createSignedJwt(visaClaimSet);
  }
}

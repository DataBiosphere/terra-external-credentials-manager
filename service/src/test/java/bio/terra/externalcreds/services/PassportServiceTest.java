package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.JwtSigningTestUtils;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import com.nimbusds.jose.JOSEException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;

class PassportServiceTest extends BaseTest {

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
  static class ValidatePassport {
    @Autowired PassportService passportService;
    @Autowired LinkedAccountDAO linkedAccountDAO;

    private static JwtSigningTestUtils jwtSigningTestUtils = new JwtSigningTestUtils();

    @BeforeAll
    static void setUpJwtVerification() throws JOSEException {
      jwtSigningTestUtils.setUpJwtVerification();
    }

    @AfterAll
    static void tearDown() {
      jwtSigningTestUtils.tearDown();
    }

    @Test
    void testValidPassportMatchingCriteria() {
      // more than one passport
      // more than one criterion
      // one criterion matches one passport
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

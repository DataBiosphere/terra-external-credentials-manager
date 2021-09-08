package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PassportServiceTest extends BaseTest {

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
        passportService.getPassport(linkedAccount.getUserId(), linkedAccount.getProviderId());

    assertPresent(loadedPassport);
    assertEquals(passport.getJwt(), savedPassport.getJwt());
    assertEquals(passport.getExpires(), savedPassport.getExpires());
  }

  @Test
  void testGetGA4GHPassportNoLinkedAccount() {
    var userId = "nonexistent_user_id";
    var providerId = "fake_provider";

    assertEmpty(passportService.getPassport(userId, providerId));
  }

  @Test
  void testGetGA4GHPassportLinkedAccountNoPassport() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    linkedAccountDAO.upsertLinkedAccount(linkedAccount);

    assertEmpty(
        passportService.getPassport(linkedAccount.getUserId(), linkedAccount.getProviderId()));
  }
}

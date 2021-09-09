package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.models.GA4GHPassport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class LinkedAccountDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;
  @Autowired private GA4GHVisaDAO visaDAO;

  @Test
  void testGetMissingLinkedAccount() {
    var shouldBeEmpty = linkedAccountDAO.getLinkedAccount("", "");
    assertEmpty(shouldBeEmpty);
  }

  @Test
  void testGetLinkedAccountById() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    var loadedLinkedAccount = linkedAccountDAO.getLinkedAccount(savedLinkedAccount.getId().get());

    // test that retrieved account matches saved account
    assertEquals(
        savedLinkedAccount.withId(loadedLinkedAccount.get().getId()), loadedLinkedAccount.get());
  }

  @Test
  void testInsertAndGetLinkedAccount() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    assertTrue(savedLinkedAccount.getId().isPresent());
    assertEquals(linkedAccount.withId(savedLinkedAccount.getId()), savedLinkedAccount);

    var loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertEquals(savedLinkedAccount, loadedLinkedAccount.get());
  }

  @Test
  void testUpsertUpdatedLinkedAccount() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    var createdLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);

    var updatedLinkedAccount =
        linkedAccountDAO.upsertLinkedAccount(
            linkedAccount
                .withRefreshToken("different_refresh")
                .withExpires(new Timestamp(200))
                .withExternalUserId(UUID.randomUUID().toString()));

    assertEquals(createdLinkedAccount.getId(), updatedLinkedAccount.getId());
    assertNotEquals(createdLinkedAccount.getRefreshToken(), updatedLinkedAccount.getRefreshToken());
    assertNotEquals(
        createdLinkedAccount.getExternalUserId(), updatedLinkedAccount.getExternalUserId());
    assertNotEquals(createdLinkedAccount.getExpires(), updatedLinkedAccount.getExpires());

    var loadedLinkedAccount =
        linkedAccountDAO.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertEquals(updatedLinkedAccount, loadedLinkedAccount.get());
  }

  @Nested
  class DeleteLinkedAccount {

    @Test
    void testDeleteLinkedAccountIfExists() {
      var createdLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      var deletionSucceeded =
          linkedAccountDAO.deleteLinkedAccountIfExists(
              createdLinkedAccount.getUserId(), createdLinkedAccount.getProviderId());
      assertTrue(deletionSucceeded);
      assertEmpty(
          linkedAccountDAO.getLinkedAccount(
              createdLinkedAccount.getUserId(), createdLinkedAccount.getProviderId()));
    }

    @Test
    void testDeleteNonexistentLinkedAccount() {
      var userId = UUID.randomUUID().toString();
      var deletionSucceeded = linkedAccountDAO.deleteLinkedAccountIfExists(userId, "fake_provider");
      assertEmpty(linkedAccountDAO.getLinkedAccount(userId, "fake_provider"));
      assertFalse(deletionSucceeded);
    }

    @Test
    void testAlsoDeletesPassport() {
      var savedLinkedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      assertPresent(savedLinkedAccount.getId());
      var passport =
          new GA4GHPassport.Builder()
              .linkedAccountId(savedLinkedAccount.getId())
              .expires(new Timestamp(100))
              .jwt("jwt")
              .build();
      passportDAO.insertPassport(passport);
      linkedAccountDAO.deleteLinkedAccountIfExists(
          savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderId());
      assertEmpty(
          passportDAO.getPassport(
              savedLinkedAccount.getUserId(), savedLinkedAccount.getProviderId()));
    }
  }

  @Nested
  class GetExpiringLinkedAccounts {

    private final Timestamp testExpirationCutoff =
        new Timestamp(Instant.now().plus(Duration.ofMinutes(15)).toEpochMilli());
    private final Timestamp nonExpiringTimestamp =
        new Timestamp(Instant.now().plus(Duration.ofMinutes(30)).toEpochMilli());

    @Test
    void testGetsOnlyExpiringLinkedAccounts() {
      // Create a linked account with a not-nearly-expired passport and visa
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport().withExpires(nonExpiringTimestamp);
      var visa = TestUtils.createRandomVisa().withExpires(nonExpiringTimestamp);

      var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
      var savedPassport =
          passportDAO.insertPassport(passport.withLinkedAccountId(savedLinkedAccount.getId()));
      visaDAO.insertVisa(visa.withPassportId(savedPassport.getId()));

      // Create a linked account with an expiring passport and visa
      var expiringLinkedAccount = TestUtils.createRandomLinkedAccount();
      var expiringPassport = TestUtils.createRandomPassport();
      var expiringVisa = TestUtils.createRandomVisa();

      var savedExpiredLinkedAccount = linkedAccountDAO.upsertLinkedAccount(expiringLinkedAccount);
      var savedExpiredPassport =
          passportDAO.insertPassport(
              expiringPassport.withLinkedAccountId(savedExpiredLinkedAccount.getId()));
      visaDAO.insertVisa(expiringVisa.withPassportId(savedExpiredPassport.getId()));

      // Assert that only the expiring linked account is returned
      assertEquals(
          List.of(savedExpiredLinkedAccount),
          linkedAccountDAO.getExpiringLinkedAccounts(testExpirationCutoff));
    }

    @Test
    void testGetsLinkedAccountWithNoVisas() {
      // Create a linked account with an expiring passport but no visas
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var expiringPassport = TestUtils.createRandomPassport();
      var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
      passportDAO.insertPassport(expiringPassport.withLinkedAccountId(savedLinkedAccount.getId()));

      // Assert that the linked account is returned
      assertEquals(
          List.of(savedLinkedAccount),
          linkedAccountDAO.getExpiringLinkedAccounts(testExpirationCutoff));
    }

    @Test
    void testGetsLinkedAccountWithNonExpiredPassportAndExpiredVisa() {
      // Create a linked account with a non-expired passport and an expired visa
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport().withExpires(nonExpiringTimestamp);
      var expiringVisa = TestUtils.createRandomVisa();

      var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
      var savedPassport =
          passportDAO.insertPassport(passport.withLinkedAccountId(savedLinkedAccount.getId()));
      visaDAO.insertVisa(expiringVisa.withPassportId(savedPassport.getId()));

      // Assert that the linked account is returned
      assertEquals(
          List.of(savedLinkedAccount),
          linkedAccountDAO.getExpiringLinkedAccounts(testExpirationCutoff));
    }
  }
}

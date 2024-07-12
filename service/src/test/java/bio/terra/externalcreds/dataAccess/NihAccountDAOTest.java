package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NihAccountDAOTest extends BaseTest {

  @Autowired private NihAccountDAO nihAccountDAO;

  @Test
  void testGetMissingNihAccount() {
    var shouldBeEmpty = nihAccountDAO.getNihAccount("missing_user_id");
    assertEmpty(shouldBeEmpty);
  }

  @Test
  void testGetNihAccountById() {
    var nihAccount = TestUtils.createRandomNihAccount();
    var savedNihAccount = nihAccountDAO.upsertNihAccount(nihAccount);
    var loadedNihAccount = nihAccountDAO.getNihAccount(savedNihAccount.getUserId());

    // test that retrieved account matches saved account
    assertEquals(
        Optional.of(savedNihAccount.withId(loadedNihAccount.get().getId())), loadedNihAccount);
  }

  @Test
  void testUpsertUpdatedNihAccount() {
    var nihAccount = TestUtils.createRandomNihAccount();
    var createdNihAccount = nihAccountDAO.upsertNihAccount(nihAccount);

    var updatedNihAccount =
        nihAccountDAO.upsertNihAccount(
            nihAccount.withExpires(
                Timestamp.from(nihAccount.getExpires().toInstant().plus(Duration.ofDays(1)))));

    assertEquals(createdNihAccount.getId(), updatedNihAccount.getId());
    assertNotEquals(createdNihAccount.getExpires(), updatedNihAccount.getExpires());
    assertEquals(createdNihAccount.getNihUsername(), updatedNihAccount.getNihUsername());

    var loadedNihAccount = nihAccountDAO.getNihAccount(nihAccount.getUserId());
    assertEquals(Optional.of(updatedNihAccount), loadedNihAccount);
  }

  @Nested
  class DeleteNihAccount {

    @Test
    void testDeleteNihAccountIfExists() {
      var createdNihAccount = nihAccountDAO.upsertNihAccount(TestUtils.createRandomNihAccount());
      var deletionSucceeded = nihAccountDAO.deleteNihAccountIfExists(createdNihAccount.getUserId());
      assertTrue(deletionSucceeded);
      assertEmpty(nihAccountDAO.getNihAccount(createdNihAccount.getUserId()));
    }

    @Test
    void testDeleteNonexistentNihAccount() {
      var userId = UUID.randomUUID().toString();
      var deletionSucceeded = nihAccountDAO.deleteNihAccountIfExists(userId);
      assertEmpty(nihAccountDAO.getNihAccount(userId));
      assertFalse(deletionSucceeded);
    }
  }

  @Nested
  class GetExpiringNihAccounts {

    @Test
    void testGetsOnlyExpiredNihAccounts() {
      // Create a non-expired nih account
      var nihAccount = TestUtils.createRandomNihAccount();

      var savedNihAccount = nihAccountDAO.upsertNihAccount(nihAccount);

      // Create an expired nih account
      var expiredNihAccount =
          TestUtils.createRandomNihAccount()
              .withExpires(Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))));

      var savedExpiredNihAccount = nihAccountDAO.upsertNihAccount(expiredNihAccount);

      // Assert that only the expired nih account is returned
      assertEquals(List.of(savedExpiredNihAccount), nihAccountDAO.getExpiredNihAccounts());

      assertEquals(List.of(savedNihAccount), nihAccountDAO.getActiveNihAccounts());
    }
  }

  @Nested
  class GetNihAccountAdminFunctionality {

    @Test
    void testGetsNihAccountByUsername() {
      var nihAccount = TestUtils.createRandomNihAccount();
      nihAccountDAO.upsertNihAccount(
          TestUtils.createRandomNihAccount()); // To make sure we get the right one.
      var savedNihAccount = nihAccountDAO.upsertNihAccount(nihAccount);
      var loadedNihAccount =
          nihAccountDAO.getNihAccountForUsername(savedNihAccount.getNihUsername());

      // Assert that only the user_id for the username we supplied
      assertEquals(nihAccount.getUserId(), loadedNihAccount.get().getUserId());
    }

    @Test
    void testGetsAllActiveNihAccounts() {
      // Create a non-expired nih account
      var nihAccount = TestUtils.createRandomNihAccount();
      var nihAccount2 = TestUtils.createRandomNihAccount();

      var savedNihAccount = nihAccountDAO.upsertNihAccount(nihAccount);
      var savedNihAccount2 = nihAccountDAO.upsertNihAccount(nihAccount2);

      // Create an expired nih account
      var expiredNihAccount =
          TestUtils.createRandomNihAccount()
              .withExpires(Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))));
      nihAccountDAO.upsertNihAccount(expiredNihAccount);

      // Assert that only the non-expired nih accounts are returned
      assertEquals(
          new HashSet<>(List.of(savedNihAccount, savedNihAccount2)),
          new HashSet<>(nihAccountDAO.getActiveNihAccounts()));
    }
  }
}

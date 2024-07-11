package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.dataAccess.NihAccountDAO;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class NihAccountServiceTest extends BaseTest {

  @Autowired private NihAccountService nihAccountService;
  @MockBean private NihAccountDAO nihAccountDAO;

  @Test
  void getNihAccountForUser() {
    when(nihAccountDAO.getNihAccount("userId"))
        .thenReturn(
            Optional.of(
                TestUtils.createRandomNihAccount()
                    .withNihUsername("nihUsername")
                    .withUserId("userId")));
    when(nihAccountDAO.getNihAccount("nonExistentUserId")).thenReturn(Optional.empty());
    assertEmpty(nihAccountService.getNihAccountForUser("nonExistentUserId"));
    var nihAccount = nihAccountService.getNihAccountForUser("userId");
    assertPresent(nihAccount);
    assertEquals("nihUsername", nihAccount.get().getNihUsername());
  }

  @Test
  void getLinkedAccountForUsername() {
    when(nihAccountDAO.getNihAccountForUsername("nihUsername"))
        .thenReturn(
            Optional.of(
                TestUtils.createRandomNihAccount()
                    .withNihUsername("nihUsername")
                    .withUserId("userId")));
    when(nihAccountDAO.getNihAccountForUsername("nonExistentUsername"))
        .thenReturn(Optional.empty());
    assertEmpty(nihAccountService.getLinkedAccountForUsername("nonExistentUsername"));
    var nihAccount = nihAccountService.getLinkedAccountForUsername("nihUsername");
    assertPresent(nihAccount);
    assertEquals("userId", nihAccount.get().getUserId());
  }

  @Test
  void upsertNihAccount() {
    var nihAccount = TestUtils.createRandomNihAccount();
    when(nihAccountDAO.upsertNihAccount(nihAccount)).thenReturn(nihAccount.withId(1));

    var upsertedNihAccount = nihAccountService.upsertNihAccount(nihAccount);
    assertEquals(nihAccount.withId(1), upsertedNihAccount);
  }

  @Test
  void deleteNihAccount() {
    when(nihAccountDAO.deleteNihAccountIfExists("userId")).thenReturn(true);
    when(nihAccountDAO.deleteNihAccountIfExists("nonExistentUserId")).thenReturn(false);
    assertTrue(nihAccountService.deleteNihAccount("userId"));
    assertFalse(nihAccountService.deleteNihAccount("nonExistentUserId"));
  }

  @Test
  void getExpiredNihAccounts() {
    var expiredNihAccount =
        TestUtils.createRandomNihAccount()
            .withId(1)
            .withExpires(Timestamp.from(Instant.now().minus(Duration.ofDays(1))));
    when(nihAccountDAO.getExpiredNihAccounts()).thenReturn(List.of(expiredNihAccount));

    var expiredNihAccounts = nihAccountService.getExpiredNihAccounts();
    assertEquals(List.of(expiredNihAccount), expiredNihAccounts);
  }

  @Test
  void getActiveNihAccounts() {
    var nonExpiredNihAccount = TestUtils.createRandomNihAccount().withId(1);
    when(nihAccountDAO.getActiveNihAccounts()).thenReturn(List.of(nonExpiredNihAccount));

    var activeNihAccounts = nihAccountService.getActiveNihAccounts();
    assertEquals(List.of(nonExpiredNihAccount), activeNihAccounts);
  }
}

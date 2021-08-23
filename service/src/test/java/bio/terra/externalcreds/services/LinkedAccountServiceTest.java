package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.ImmutableGA4GHPassport;
import bio.terra.externalcreds.models.ImmutableGA4GHVisa;
import bio.terra.externalcreds.models.ImmutableLinkedAccount;
import bio.terra.externalcreds.models.ImmutableLinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.LinkedAccount;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class LinkedAccountServiceTest extends BaseTest {

  @Autowired private LinkedAccountService linkedAccountService;
  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;
  @Autowired private GA4GHVisaDAO visaDAO;

  @Test
  void testGetLinkedAccount() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    linkedAccountDAO.upsertLinkedAccount(linkedAccount);

    var savedLinkedAccount =
        linkedAccountService.getLinkedAccount(
            linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertPresent(savedLinkedAccount);
    assertEquals(
        linkedAccount,
        savedLinkedAccount.map(ImmutableLinkedAccount::copyOf).get().withId(Optional.empty()));
  }

  @Test
  void testGetGA4GHPassport() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    var passport = TestUtils.createRandomPassport();

    saveAndValidateLinkedAccount(linkedAccount, passport, Collections.emptyList());

    var savedPassport =
        linkedAccountService.getGA4GHPassport(
            linkedAccount.getUserId(), linkedAccount.getProviderId());

    assertPresent(savedPassport);
    assertEquals(passport.getJwt(), savedPassport.get().getJwt());
    assertEquals(passport.getExpires(), savedPassport.get().getExpires());
  }

  @Test
  void testGetGA4GHPassportNoLinkedAccount() {
    var userId = "nonexistent_user_id";
    var providerId = "fake_provider";

    assertEmpty(linkedAccountService.getGA4GHPassport(userId, providerId));
  }

  @Test
  void testGetGA4GHPassportLinkedAccountNoPassport() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    saveAndValidateLinkedAccount(linkedAccount, null, Collections.emptyList());

    assertEmpty(
        linkedAccountService.getGA4GHPassport(
            linkedAccount.getUserId(), linkedAccount.getProviderId()));
  }

  @Test
  void testSaveLinkedAccountWithPassportAndVisas() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    var passport = TestUtils.createRandomPassport();
    var visas =
        List.of(
            TestUtils.createRandomVisa(),
            TestUtils.createRandomVisa(),
            TestUtils.createRandomVisa());
    var savedLinkedAccount1 = saveAndValidateLinkedAccount(linkedAccount, passport, visas);

    // save again with new passport and visas to test overwrite
    var passport2 = TestUtils.createRandomPassport();
    var visas2 =
        List.of(
            TestUtils.createRandomVisa(),
            TestUtils.createRandomVisa(),
            TestUtils.createRandomVisa());
    var savedLinkedAccount2 = saveAndValidateLinkedAccount(linkedAccount, passport2, visas2);

    // saved linked accounts should the same
    assertEquals(savedLinkedAccount1, savedLinkedAccount2);
  }

  @Test
  void testSaveLinkedAccountWithPassportNoVisas() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    var passport = TestUtils.createRandomPassport();
    saveAndValidateLinkedAccount(linkedAccount, passport, Collections.emptyList());
  }

  @Test
  void testSaveLinkedAccountWithoutPassport() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    saveAndValidateLinkedAccount(linkedAccount, null, Collections.emptyList());
  }

  @Test
  void testDeleteNonExistingLinkedAccount() {
    var linkedAccount = TestUtils.createRandomLinkedAccount();
    assertFalse(
        linkedAccountService.deleteLinkedAccount(
            linkedAccount.getUserId(), linkedAccount.getProviderId()));
  }

  private LinkedAccount saveAndValidateLinkedAccount(
      LinkedAccount linkedAccount, GA4GHPassport passport, List<ImmutableGA4GHVisa> visas) {
    var saved =
        linkedAccountService.saveLinkedAccount(
            ImmutableLinkedAccountWithPassportAndVisas.builder()
                .linkedAccount(linkedAccount)
                .passport(Optional.ofNullable(passport))
                .visas(visas)
                .build());

    assertEquals(
        linkedAccount,
        ImmutableLinkedAccount.copyOf(saved.getLinkedAccount()).withId(Optional.empty()));
    assertTrue(saved.getLinkedAccount().getId().isPresent());

    var savedPassport =
        passportDAO.getPassport(
            saved.getLinkedAccount().getUserId(), saved.getLinkedAccount().getProviderId());
    if (passport == null) {
      assertEmpty(savedPassport);
    } else {
      assertPresent(savedPassport);
      assertPresent(savedPassport.get().getId());
      assertEquals(
          passport,
          savedPassport
              .map(ImmutableGA4GHPassport::copyOf)
              .get()
              .withId(Optional.empty())
              .withLinkedAccountId(Optional.empty()));
      var savedVisas = visaDAO.listVisas(savedPassport.get().getId().get());
      assertEquals(
          visas,
          savedVisas.stream()
              .map(
                  v ->
                      ImmutableGA4GHVisa.copyOf(v)
                          .withId(Optional.empty())
                          .withPassportId(Optional.empty()))
              .collect(Collectors.toList()));
    }

    return saved.getLinkedAccount();
  }
}

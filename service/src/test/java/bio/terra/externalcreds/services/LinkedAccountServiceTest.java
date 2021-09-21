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
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.visaComparators.VisaComparator;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;

public class LinkedAccountServiceTest extends BaseTest {

  @Nested
  @TestComponent
  class GetLinkedAccount {

    @Autowired private LinkedAccountDAO linkedAccountDAO;
    @Autowired private LinkedAccountService linkedAccountService;

    @Test
    void testSaveAndGetLinkedAccount() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      linkedAccountDAO.upsertLinkedAccount(linkedAccount);

      var savedLinkedAccount =
          linkedAccountService.getLinkedAccount(
              linkedAccount.getUserId(), linkedAccount.getProviderId());
      assertPresent(savedLinkedAccount);
      assertEquals(linkedAccount, savedLinkedAccount.get().withId(Optional.empty()));
    }
  }

  @Nested
  @TestComponent
  class UpsertLinkedAccountWithPassportAndVisas {

    @Autowired private LinkedAccountService linkedAccountService;
    @Autowired private GA4GHPassportDAO passportDAO;
    @Autowired private GA4GHVisaDAO visaDAO;

    @MockBean(name = "test1")
    private VisaComparator testVisaComparator;

    @MockBean(name = "test2")
    private VisaComparator testVisaComparator2;

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


    private LinkedAccount saveAndValidateLinkedAccount(
        LinkedAccount linkedAccount, GA4GHPassport passport, List<GA4GHVisa> visas) {
      var saved =
          linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
              new LinkedAccountWithPassportAndVisas.Builder()
                  .linkedAccount(linkedAccount)
                  .passport(Optional.ofNullable(passport))
                  .visas(visas)
                  .build());

      assertEquals(linkedAccount, saved.getLinkedAccount().withId(Optional.empty()));
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
            savedPassport.get().withId(Optional.empty()).withLinkedAccountId(Optional.empty()));
        var savedVisas =
            visaDAO.listVisas(
                saved.getLinkedAccount().getUserId(), saved.getLinkedAccount().getProviderId());
        assertEquals(
            visas,
            savedVisas.stream()
                .map(v -> v.withId(Optional.empty()).withPassportId(Optional.empty()))
                .collect(Collectors.toList()));
      }

      return saved.getLinkedAccount();
    }
  }

  @Nested
  @TestComponent
  class DeleteLinkedAccount {
    @Autowired private LinkedAccountService linkedAccountService;

    @Test
    void testDeleteNonExistingLinkedAccount() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      assertFalse(
          linkedAccountService.deleteLinkedAccount(
              linkedAccount.getUserId(), linkedAccount.getProviderId()));
    }
  }

  // save new linked account emits event - 2 visa types
  // save linked account with visa with same auth does not emit event - 2 visa types
  // save linked account with visa with different auth does emit event - 2 visa types

  // deleting linked account with no visas does not emit event
  // deleting linked account with visas does emit event
}

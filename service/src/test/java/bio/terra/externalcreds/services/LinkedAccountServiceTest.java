package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.AuthorizationChangeEvent;
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
import org.mockito.Mockito;
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
      var savedLinkedAccount1 =
          saveAndValidateLinkedAccount(
              linkedAccount, passport, visas, linkedAccountService, passportDAO, visaDAO);

      // save again with new passport and visas to test overwrite
      var passport2 = TestUtils.createRandomPassport();
      var visas2 =
          List.of(
              TestUtils.createRandomVisa(),
              TestUtils.createRandomVisa(),
              TestUtils.createRandomVisa());
      var savedLinkedAccount2 =
          saveAndValidateLinkedAccount(
              linkedAccount, passport2, visas2, linkedAccountService, passportDAO, visaDAO);

      // saved linked accounts should the same
      assertEquals(savedLinkedAccount1, savedLinkedAccount2);
    }

    @Test
    void testSaveLinkedAccountWithPassportNoVisas() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();
      saveAndValidateLinkedAccount(
          linkedAccount,
          passport,
          Collections.emptyList(),
          linkedAccountService,
          passportDAO,
          visaDAO);
    }

    @Test
    void testSaveLinkedAccountWithoutPassport() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      saveAndValidateLinkedAccount(
          linkedAccount, null, Collections.emptyList(), linkedAccountService, passportDAO, visaDAO);
    }

    @Test
    void testEmitsEvents() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();
      var visa1 = TestUtils.createRandomVisa().withVisaType("type1");
      var visa2 = TestUtils.createRandomVisa().withVisaType("type2");

      var linkedAccountServiceSpy = Mockito.spy(linkedAccountService);
      var expectedEvent =
          new AuthorizationChangeEvent.Builder()
              .providerId(linkedAccount.getProviderId())
              .userId(linkedAccount.getUserId())
              .build();

      // save new linked account with 2 visas of different types
      var saved =
          linkedAccountServiceSpy.upsertLinkedAccountWithPassportAndVisas(
              new LinkedAccountWithPassportAndVisas.Builder()
                  .linkedAccount(linkedAccount)
                  .passport(Optional.ofNullable(passport))
                  .visas(List.of(visa1, visa2))
                  .build());
      // check that an event was emitted
      // verify(linkedAccountServiceSpy).publishAuthorizationChangeEvent(any()); // expectedEvent);

      // upsert the same linked account + visas
      // use a spy to check that nothing was emitted

      // upsert the linked account with visas with different auth (2 different visa types)
      // use a spy to check that an event was emitted

      // To figure out:
      // - Do we need to mock the comparator result?
    }
  }

  @Nested
  @TestComponent
  class DeleteLinkedAccount {
    @Autowired private LinkedAccountService linkedAccountService;
    @Autowired private GA4GHPassportDAO passportDAO;
    @Autowired private GA4GHVisaDAO visaDAO;

    @MockBean private EventPublisher eventPublisherMock;

    @Test
    void testDeleteNonExistingLinkedAccount() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      assertFalse(
          linkedAccountService.deleteLinkedAccount(
              linkedAccount.getUserId(), linkedAccount.getProviderId()));

      Mockito.verify(eventPublisherMock, never())
          .publishAuthorizationChangeEvent(
              new AuthorizationChangeEvent.Builder()
                  .userId(linkedAccount.getUserId())
                  .providerId(linkedAccount.getProviderId())
                  .build());
    }

    @Test
    void testDeleteExistingLinkedAccount() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();
      var visas =
          List.of(
              TestUtils.createRandomVisa(),
              TestUtils.createRandomVisa(),
              TestUtils.createRandomVisa());
      var savedLinkedAccount1 =
          saveAndValidateLinkedAccount(
              linkedAccount, passport, visas, linkedAccountService, passportDAO, visaDAO);

      assertTrue(
          linkedAccountService.deleteLinkedAccount(
              linkedAccount.getUserId(), linkedAccount.getProviderId()));

      assertEmpty(linkedAccountService.getLinkedAccount(savedLinkedAccount1.getId().get()));

      // Check that an event was emitted twice, during both insertion and deletion
      Mockito.verify(eventPublisherMock, times(2))
          .publishAuthorizationChangeEvent(
              new AuthorizationChangeEvent.Builder()
                  .userId(linkedAccount.getUserId())
                  .providerId(linkedAccount.getProviderId())
                  .build());
    }

    @Test
    void testDoesNotEmitEventWhenNoVisas() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();

      saveAndValidateLinkedAccount(
          linkedAccount,
          passport,
          Collections.emptyList(),
          linkedAccountService,
          passportDAO,
          visaDAO);

      linkedAccountService.deleteLinkedAccount(
          linkedAccount.getUserId(), linkedAccount.getProviderId());

      Mockito.verify(eventPublisherMock, never())
          .publishAuthorizationChangeEvent(
              new AuthorizationChangeEvent.Builder()
                  .userId(linkedAccount.getUserId())
                  .providerId(linkedAccount.getProviderId())
                  .build());
    }
  }

  private LinkedAccount saveAndValidateLinkedAccount(
      LinkedAccount linkedAccount,
      GA4GHPassport passport,
      List<GA4GHVisa> visas,
      LinkedAccountService linkedAccountService,
      GA4GHPassportDAO passportDAO,
      GA4GHVisaDAO visaDAO) {
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

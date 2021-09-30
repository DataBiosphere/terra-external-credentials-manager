package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.mockito.ArgumentMatcher;
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
              linkedAccount.getUserId(), linkedAccount.getProviderName());
      assertPresent(savedLinkedAccount);
      assertEquals(linkedAccount, savedLinkedAccount.get().withId(Optional.empty()));
    }
  }

  @Nested
  @TestComponent
  class UpsertLinkedAccountWithPassportAndVisas {

    @MockBean EventPublisher eventPublisherMock;
    @Autowired private LinkedAccountService linkedAccountService;
    @Autowired private GA4GHPassportDAO passportDAO;
    @Autowired private GA4GHVisaDAO visaDAO;

    @MockBean(name = "test1")
    private VisaComparator visaComparatorMock1;

    @MockBean(name = "test2")
    private VisaComparator visaComparatorMock2;

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
    void testNewAccountEmitsEvent() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();
      var visa1 = TestUtils.createRandomVisa();

      var expectedEvent =
          new AuthorizationChangeEvent.Builder()
              .providerId(linkedAccount.getProviderName())
              .userId(linkedAccount.getUserId())
              .build();

      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount)
              .passport(Optional.ofNullable(passport))
              .visas(List.of(visa1))
              .build());

      verify(eventPublisherMock).publishAuthorizationChangeEvent(expectedEvent);
    }

    @Test
    void testNewAccountWithNoVisaDoesNotEmitEvent() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();

      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount)
              .passport(Optional.ofNullable(passport))
              .visas(Collections.emptyList())
              .build());

      verify(eventPublisherMock, never()).publishAuthorizationChangeEvent(any());
    }

    @Test
    void testRemoveVisaEmitsEvent() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();
      var visa1 = TestUtils.createRandomVisa().withVisaType("type1");
      var visa2 = TestUtils.createRandomVisa().withVisaType("type2");

      var expectedEvent =
          new AuthorizationChangeEvent.Builder()
              .providerId(linkedAccount.getProviderName())
              .userId(linkedAccount.getUserId())
              .build();

      setupVisaComparatorMocks(visa1, visa2);

      // save new linked account with 2 visas of different types
      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount)
              .passport(Optional.ofNullable(passport))
              .visas(List.of(visa1, visa2))
              .build());

      // upsert with a removed visa
      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount)
              .passport(Optional.ofNullable(passport))
              .visas(List.of(visa1))
              .build());

      // check that 2 events were emitted once for each upsert
      verify(eventPublisherMock, times(2)).publishAuthorizationChangeEvent(expectedEvent);
    }

    @Test
    void testNoChangeDoesNotEmitEvent() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();
      var visa1 = TestUtils.createRandomVisa().withVisaType("type1");
      var visa2 = TestUtils.createRandomVisa().withVisaType("type2");

      setupVisaComparatorMocks(visa1, visa2);

      // save new linked account with 2 visas of different types
      var saved =
          linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
              new LinkedAccountWithPassportAndVisas.Builder()
                  .linkedAccount(linkedAccount)
                  .passport(Optional.ofNullable(passport))
                  .visas(List.of(visa1, visa2))
                  .build());

      // check that only 1 event emitted so far
      verify(eventPublisherMock, times(1)).publishAuthorizationChangeEvent(any());

      // upsert the same linked account + visas
      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(saved);

      // check that still only 1 event emitted, the second upsert did not emit
      verify(eventPublisherMock, times(1)).publishAuthorizationChangeEvent(any());
    }

    @Test
    void testVisaChangeEmitsEvent() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();
      var visa1 = TestUtils.createRandomVisa().withVisaType("type1");
      var visa2 = TestUtils.createRandomVisa().withVisaType("type2");

      var expectedEvent =
          new AuthorizationChangeEvent.Builder()
              .providerId(linkedAccount.getProviderName())
              .userId(linkedAccount.getUserId())
              .build();

      setupVisaComparatorMocks(visa1, visa2);

      // save new linked account with 2 visas of different types
      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount)
              .passport(Optional.ofNullable(passport))
              .visas(List.of(visa1, visa2))
              .build());

      // upsert with a changed visa
      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount)
              .passport(Optional.ofNullable(passport))
              .visas(List.of(visa1, visa2.withJwt("different")))
              .build());

      // check that 2 events were emitted once for each upsert
      verify(eventPublisherMock, times(2)).publishAuthorizationChangeEvent(expectedEvent);
    }

    @Test
    void testDegenerateVisasEmitsEvent() {
      var linkedAccount = TestUtils.createRandomLinkedAccount();
      var passport = TestUtils.createRandomPassport();
      var visa1 = TestUtils.createRandomVisa().withVisaType("type1");
      var visa2 = TestUtils.createRandomVisa().withVisaType("type2");

      var expectedEvent =
          new AuthorizationChangeEvent.Builder()
              .providerId(linkedAccount.getProviderName())
              .userId(linkedAccount.getUserId())
              .build();

      setupVisaComparatorMocks(visa1, visa2);

      // save new linked account with 2 visas of different types
      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount)
              .passport(Optional.ofNullable(passport))
              .visas(List.of(visa1, visa1, visa2))
              .build());

      linkedAccountService.upsertLinkedAccountWithPassportAndVisas(
          new LinkedAccountWithPassportAndVisas.Builder()
              .linkedAccount(linkedAccount)
              .passport(Optional.ofNullable(passport))
              .visas(List.of(visa1, visa2, visa2))
              .build());

      // check that 2 events were emitted once for each upsert
      verify(eventPublisherMock, times(2)).publishAuthorizationChangeEvent(expectedEvent);
    }

    private void setupVisaComparatorMocks(GA4GHVisa visa1, GA4GHVisa visa2) {
      // The default return type is false, so we only need to mock the cases which return true
      VisaJwtMatcher visa1Matcher = new VisaJwtMatcher(visa1);
      VisaJwtMatcher visa2Matcher = new VisaJwtMatcher(visa2);

      when(visaComparatorMock1.authorizationsMatch(argThat(visa1Matcher), argThat(visa1Matcher)))
          .thenReturn(true);
      when(visaComparatorMock2.authorizationsMatch(argThat(visa2Matcher), argThat(visa2Matcher)))
          .thenReturn(true);

      when(visaComparatorMock1.visaTypeSupported(argThat(new VisaTypeMatcher(visa1))))
          .thenReturn(true);
      when(visaComparatorMock2.visaTypeSupported(argThat(new VisaTypeMatcher(visa2))))
          .thenReturn(true);
    }

    // A custom ArgumentMatcher to use in mocks that matches visas with the same type
    class VisaTypeMatcher implements ArgumentMatcher<GA4GHVisa> {
      private final GA4GHVisa left;

      public VisaTypeMatcher(GA4GHVisa visaToMatch) {
        this.left = visaToMatch;
      }

      @Override
      public boolean matches(GA4GHVisa right) {
        return right != null && left.getVisaType().equals(right.getVisaType());
      }
    }

    // A custom ArgumentMatcher to use in mocks that matches visas with the same jwt
    class VisaJwtMatcher implements ArgumentMatcher<GA4GHVisa> {
      private final GA4GHVisa left;

      public VisaJwtMatcher(GA4GHVisa visaToMatch) {
        this.left = visaToMatch;
      }

      @Override
      public boolean matches(GA4GHVisa right) {
        return right != null && left.getJwt().equals(right.getJwt());
      }
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
              linkedAccount.getUserId(), linkedAccount.getProviderName()));

      Mockito.verify(eventPublisherMock, never())
          .publishAuthorizationChangeEvent(
              new AuthorizationChangeEvent.Builder()
                  .userId(linkedAccount.getUserId())
                  .providerId(linkedAccount.getProviderName())
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
              linkedAccount.getUserId(), linkedAccount.getProviderName()));

      assertEmpty(linkedAccountService.getLinkedAccount(savedLinkedAccount1.getId().get()));

      // Check that an event was emitted twice, during both insertion and deletion
      Mockito.verify(eventPublisherMock, times(2))
          .publishAuthorizationChangeEvent(
              new AuthorizationChangeEvent.Builder()
                  .userId(linkedAccount.getUserId())
                  .providerId(linkedAccount.getProviderName())
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
          linkedAccount.getUserId(), linkedAccount.getProviderName());

      verify(eventPublisherMock, never())
          .publishAuthorizationChangeEvent(
              new AuthorizationChangeEvent.Builder()
                  .userId(linkedAccount.getUserId())
                  .providerId(linkedAccount.getProviderName())
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
            saved.getLinkedAccount().getUserId(), saved.getLinkedAccount().getProviderName());
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
              saved.getLinkedAccount().getUserId(), saved.getLinkedAccount().getProviderName());
      assertEquals(
          visas,
          savedVisas.stream()
              .map(v -> v.withId(Optional.empty()).withPassportId(Optional.empty()))
              .collect(Collectors.toList()));
    }

    return saved.getLinkedAccount();
  }
}

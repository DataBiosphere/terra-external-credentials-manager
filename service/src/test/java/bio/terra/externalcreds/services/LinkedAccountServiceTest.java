package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

public class LinkedAccountServiceTest extends BaseTest {
  @Autowired private LinkedAccountService linkedAccountService;
  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;
  @Autowired private GA4GHVisaDAO visaDAO;

  private static final Supplier<Timestamp> randomTimestamp =
      () -> new Timestamp(System.currentTimeMillis());
  private static final Supplier<GA4GHPassport> randomPassport =
      () ->
          GA4GHPassport.builder()
              .jwt(UUID.randomUUID().toString())
              .expires(randomTimestamp.get())
              .build();
  private static final Supplier<LinkedAccount> randomLinkedAccount =
      () ->
          LinkedAccount.builder()
              .expires(randomTimestamp.get())
              .providerId(UUID.randomUUID().toString())
              .refreshToken(UUID.randomUUID().toString())
              .userId(UUID.randomUUID().toString())
              .externalUserId(UUID.randomUUID().toString())
              .build();
  private static final Supplier<GA4GHVisa> randomVisa =
      () ->
          GA4GHVisa.builder()
              .visaType(UUID.randomUUID().toString())
              .tokenType(TokenTypeEnum.access_token)
              .expires(randomTimestamp.get())
              .issuer(UUID.randomUUID().toString())
              .jwt(UUID.randomUUID().toString())
              .build();

  private LinkedAccount saveAndValidateLinkedAccount(
      LinkedAccount linkedAccount, GA4GHPassport passport, List<GA4GHVisa> visas) {
    var savedLinkedAccount =
        linkedAccountService.saveLinkedAccount(
            LinkedAccountWithPassportAndVisas.builder()
                .linkedAccount(linkedAccount)
                .passport(passport)
                .visas(visas)
                .build());

    assertEquals(linkedAccount, savedLinkedAccount.withId(0));
    assertTrue(savedLinkedAccount.getId() > 0);

    var savedPassport = passportDAO.getPassport(savedLinkedAccount.getId());
    if (passport == null) {
      assertNull(savedPassport);
    } else {
      assertEquals(passport, savedPassport.withId(0).withLinkedAccountId(0));
      var savedVisas = visaDAO.listVisas(savedPassport.getId());
      assertEquals(
          visas,
          savedVisas.stream().map(v -> v.withId(0).withPassportId(0)).collect(Collectors.toList()));
    }

    return savedLinkedAccount;
  }

  @Test
  @Transactional
  @Rollback
  void testGetLinkedAccount() {
    var linkedAccount = randomLinkedAccount.get();
    linkedAccountDAO.upsertLinkedAccount(linkedAccount);

    var savedLinkedAccount =
        linkedAccountService.getLinkedAccount(
            linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertEquals(linkedAccount, savedLinkedAccount.withId(0));
  }

  @Test
  @Transactional
  @Rollback
  void testSaveLinkedAccountWithPassportAndVisas() {
    var linkedAccount = randomLinkedAccount.get();
    var passport = randomPassport.get();
    var visas = List.of(randomVisa.get(), randomVisa.get(), randomVisa.get());
    var savedLinkedAccount1 = saveAndValidateLinkedAccount(linkedAccount, passport, visas);

    // save again with new passport and visas to test overwrite
    var passport2 = randomPassport.get();
    var visas2 = List.of(randomVisa.get(), randomVisa.get(), randomVisa.get());
    var savedLinkedAccount2 = saveAndValidateLinkedAccount(linkedAccount, passport2, visas2);

    // saved linked accounts should the same
    assertEquals(savedLinkedAccount1, savedLinkedAccount2);
  }

  @Test
  @Transactional
  @Rollback
  void testSaveLinkedAccountWithPassportNoVisas() {
    var linkedAccount = randomLinkedAccount.get();
    var passport = randomPassport.get();
    saveAndValidateLinkedAccount(linkedAccount, passport, Collections.emptyList());
  }

  @Test
  @Transactional
  @Rollback
  void testSaveLinkedAccountWithoutPassport() {
    var linkedAccount = randomLinkedAccount.get();
    saveAndValidateLinkedAccount(linkedAccount, null, Collections.emptyList());
  }
}

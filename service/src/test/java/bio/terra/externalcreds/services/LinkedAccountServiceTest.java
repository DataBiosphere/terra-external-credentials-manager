package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    var linkedAccount = createRandomLinkedAccount();
    linkedAccountDAO.upsertLinkedAccount(linkedAccount);

    var savedLinkedAccount =
        linkedAccountService.getLinkedAccount(
            linkedAccount.getUserId(), linkedAccount.getProviderId());
    assertPresent(savedLinkedAccount);
    assertEquals(linkedAccount, savedLinkedAccount.get().withId(0));
  }

  @Test
  void testSaveLinkedAccountWithPassportAndVisas() {
    var linkedAccount = createRandomLinkedAccount();
    var passport = createRandomPassport();
    var visas = List.of(createRandomVisa(), createRandomVisa(), createRandomVisa());
    var savedLinkedAccount1 = saveAndValidateLinkedAccount(linkedAccount, passport, visas);

    // save again with new passport and visas to test overwrite
    var passport2 = createRandomPassport();
    var visas2 = List.of(createRandomVisa(), createRandomVisa(), createRandomVisa());
    var savedLinkedAccount2 = saveAndValidateLinkedAccount(linkedAccount, passport2, visas2);

    // saved linked accounts should the same
    assertEquals(savedLinkedAccount1, savedLinkedAccount2);
  }

  @Test
  void testSaveLinkedAccountWithPassportNoVisas() {
    var linkedAccount = createRandomLinkedAccount();
    var passport = createRandomPassport();
    saveAndValidateLinkedAccount(linkedAccount, passport, Collections.emptyList());
  }

  @Test
  void testSaveLinkedAccountWithoutPassport() {
    var linkedAccount = createRandomLinkedAccount();
    saveAndValidateLinkedAccount(linkedAccount, null, Collections.emptyList());
  }

  @Test
  void testDeleteNonExistingLinkedAccount() {
    var linkedAccount = createRandomLinkedAccount();
    assertFalse(
        linkedAccountService.deleteLinkedAccount(
            linkedAccount.getUserId(), linkedAccount.getProviderId()));
  }

  private LinkedAccount saveAndValidateLinkedAccount(
      LinkedAccount linkedAccount, GA4GHPassport passport, List<GA4GHVisa> visas) {
    var saved =
        linkedAccountService.saveLinkedAccount(
            LinkedAccountWithPassportAndVisas.builder()
                .linkedAccount(linkedAccount)
                .passport(passport)
                .visas(visas)
                .build());

    assertEquals(linkedAccount, saved.getLinkedAccount().withId(0));
    assertTrue(saved.getLinkedAccount().getId() > 0);

    var savedPassport = passportDAO.getPassport(saved.getLinkedAccount().getId());
    if (passport == null) {
      assertEmpty(savedPassport);
    } else {
      assertPresent(savedPassport);
      assertEquals(passport, savedPassport.get().withId(0).withLinkedAccountId(0));
      var savedVisas = visaDAO.listVisas(savedPassport.get().getId());
      assertEquals(
          visas,
          savedVisas.stream().map(v -> v.withId(0).withPassportId(0)).collect(Collectors.toList()));
    }

    return saved.getLinkedAccount();
  }

  private Timestamp getRandomTimestamp() {
    return new Timestamp(System.currentTimeMillis());
  }

  private LinkedAccount createRandomLinkedAccount() {
    return LinkedAccount.builder()
        .expires(getRandomTimestamp())
        .providerId(UUID.randomUUID().toString())
        .refreshToken(UUID.randomUUID().toString())
        .userId(UUID.randomUUID().toString())
        .externalUserId(UUID.randomUUID().toString())
        .build();
  }

  private GA4GHPassport createRandomPassport() {
    return GA4GHPassport.builder()
        .jwt(UUID.randomUUID().toString())
        .expires(getRandomTimestamp())
        .build();
  }

  private GA4GHVisa createRandomVisa() {
    return GA4GHVisa.builder()
        .visaType(UUID.randomUUID().toString())
        .tokenType(TokenTypeEnum.access_token)
        .expires(getRandomTimestamp())
        .issuer(UUID.randomUUID().toString())
        .jwt(UUID.randomUUID().toString())
        .build();
  }
}

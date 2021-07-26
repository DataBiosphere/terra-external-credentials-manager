package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

public class GA4GHPassportDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;
  @Autowired private GA4GHVisaDAO visaDAO;

  private GA4GHPassport passport;
  private LinkedAccount linkedAccount;

  @BeforeEach
  void setup() {
    linkedAccount =
        LinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .externalUserId("externalUser")
            .build();

    passport = GA4GHPassport.builder().jwt("fake-jwt").expires(new Timestamp(100)).build();
  }

  @Test
  void testMissingPassport() {
    var shouldBeEmpty = passportDAO.getPassport(-1);
    assertTrue(shouldBeEmpty.isEmpty());
  }

  @Test
  void testCreateAndGetPassport() {
    var savedAccountId = linkedAccountDAO.upsertLinkedAccount(linkedAccount).getId();
    var savedPassport = passportDAO.insertPassport(passport.withLinkedAccountId(savedAccountId));
    assertTrue(savedPassport.getId() > 0);
    assertEquals(
        passport
            .withId(savedPassport.getId())
            .withLinkedAccountId(savedPassport.getLinkedAccountId()),
        savedPassport);

    var loadedPassport = passportDAO.getPassport(savedAccountId);
    assertEquals(Optional.of(savedPassport), loadedPassport);
  }

  @Test
  void testPassportIsUniqueForLinkedAccount() {
    var savedAccountId = linkedAccountDAO.upsertLinkedAccount(linkedAccount).getId();
    var savedPassport = passportDAO.insertPassport(passport.withLinkedAccountId(savedAccountId));

    assertThrows(
        DuplicateKeyException.class,
        () ->
            passportDAO.insertPassport(
                savedPassport.withExpires(new Timestamp(200)).withJwt("different-jwt")));
  }

  @Nested
  class DeletePassport {

    @Test
    void testDeletePassportIfExists() {
      var savedAccountId = linkedAccountDAO.upsertLinkedAccount(linkedAccount).getId();
      passportDAO.insertPassport(passport.withLinkedAccountId(savedAccountId));

      assertTrue(passportDAO.getPassport(savedAccountId).isPresent());
      assertTrue(passportDAO.deletePassport(savedAccountId));
      assertTrue(passportDAO.getPassport(savedAccountId).isEmpty());
    }

    @Test
    void testDeleteNonexistentPassport() {
      assertFalse(passportDAO.deletePassport(-1));
    }

    @Test
    void testAlsoDeletesVisa() {
      var savedAccountId = linkedAccountDAO.upsertLinkedAccount(linkedAccount).getId();
      var savedPassport = passportDAO.insertPassport(passport.withLinkedAccountId(savedAccountId));

      visaDAO.insertVisa(
          GA4GHVisa.builder()
              .visaType("fake")
              .passportId(savedPassport.getId())
              .tokenType(TokenTypeEnum.access_token)
              .expires(new Timestamp(150))
              .issuer("fake-issuer")
              .lastValidated(new Timestamp(125))
              .jwt("fake-jwt")
              .build());

      assertFalse(visaDAO.listVisas(savedPassport.getId()).isEmpty());
      assertTrue(passportDAO.deletePassport(savedAccountId));
      assertTrue(visaDAO.listVisas(savedPassport.getId()).isEmpty());
    }
  }
}

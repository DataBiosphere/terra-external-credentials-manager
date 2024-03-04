package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.sql.Timestamp;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;

class GA4GHPassportDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;
  @Autowired private GA4GHVisaDAO visaDAO;

  @MockBean private ExternalCredsConfig externalCredsConfig;

  @Test
  void testGetMissingPassport() {
    var shouldBeEmpty = passportDAO.getPassport("nonexistent_user_id", Provider.RAS);
    assertEmpty(shouldBeEmpty);
  }

  @Nested
  class CreatePassport {

    @Test
    void testCreateAndGetPassport() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomPassportLinkedAccount());
      assertPresent(savedAccount.getId());

      var passport = TestUtils.createRandomPassport();
      var savedPassport =
          passportDAO.insertPassport(passport.withLinkedAccountId(savedAccount.getId()));

      assertTrue(savedPassport.getId().isPresent());
      assertEquals(
          passport
              .withId(savedPassport.getId())
              .withLinkedAccountId(savedPassport.getLinkedAccountId()),
          savedPassport);

      var loadedPassport = passportDAO.getPassport(savedAccount.getUserId(), Provider.RAS);
      assertEquals(Optional.of(savedPassport), loadedPassport);
    }

    @Test
    void testCreateDuplicatePassportThrows() {
      var savedAccountId =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount()).getId();
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(savedAccountId));

      var duplicatePassport =
          savedPassport
              .withExpires(new Timestamp(200))
              .withJwt("different-jwt")
              .withJwtId("different-jwt-id");
      assertThrows(
          DuplicateKeyException.class, () -> passportDAO.insertPassport(duplicatePassport));
    }

    @Test
    void testCreatePassportWithDuplicateJwtIdThrows() {
      var savedAccountId1 =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount()).getId();
      var savedAccountId2 =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount()).getId();
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(savedAccountId1));

      var duplicatePassport = savedPassport.withLinkedAccountId(savedAccountId2);
      assertThrows(
          DuplicateKeyException.class, () -> passportDAO.insertPassport(duplicatePassport));
    }
  }

  @Nested
  class DeletePassport {

    @Test
    void testDeletePassportIfExists() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomPassportLinkedAccount());
      assertPresent(savedAccount.getId());
      passportDAO.insertPassport(
          TestUtils.createRandomPassport().withLinkedAccountId(savedAccount.getId()));

      assertPresent(passportDAO.getPassport(savedAccount.getUserId(), savedAccount.getProvider()));
      assertTrue(passportDAO.deletePassport(savedAccount.getId().get()));
      assertEmpty(passportDAO.getPassport(savedAccount.getUserId(), savedAccount.getProvider()));
    }

    @Test
    void testDeleteNonexistentPassport() {
      assertFalse(passportDAO.deletePassport(-1));
    }

    @Test
    void testAlsoDeletesVisa() {
      var linkedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomPassportLinkedAccount());
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(linkedAccount.getId()));

      visaDAO.insertVisa(
          new GA4GHVisa.Builder()
              .visaType("fake")
              .passportId(savedPassport.getId())
              .tokenType(TokenTypeEnum.access_token)
              .expires(new Timestamp(150))
              .issuer("fake-issuer")
              .lastValidated(new Timestamp(125))
              .jwt("fake-jwt")
              .build());

      assertFalse(
          visaDAO.listVisas(linkedAccount.getUserId(), linkedAccount.getProvider()).isEmpty());
      assertTrue(passportDAO.deletePassport(linkedAccount.getId().get()));
      assertTrue(
          visaDAO.listVisas(linkedAccount.getUserId(), linkedAccount.getProvider()).isEmpty());
    }
  }
}

package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.models.ImmutableGA4GHPassport;
import bio.terra.externalcreds.models.ImmutableGA4GHVisa;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.sql.Timestamp;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

public class GA4GHPassportDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;
  @Autowired private GA4GHVisaDAO visaDAO;

  @Test
  void testGetMissingPassport() {
    var shouldBeEmpty = passportDAO.getPassport("nonexistent_user_id", "nonexistent_provider_id");
    assertEmpty(shouldBeEmpty);
  }

  @Nested
  class CreatePassport {

    @Test
    void testCreateAndGetPassport() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
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

      var loadedPassport =
          passportDAO.getPassport(savedAccount.getUserId(), savedAccount.getProviderId());
      assertEquals(Optional.of(savedPassport), loadedPassport);
    }

    @Test
    void testCreateDuplicatePassportThrows() {
      var savedAccountId =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount()).getId();
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(savedAccountId));

      assertThrows(
          DuplicateKeyException.class,
          () ->
              passportDAO.insertPassport(
                  ImmutableGA4GHPassport.copyOf(savedPassport)
                      .withExpires(new Timestamp(200))
                      .withJwt("different-jwt")));
    }
  }

  @Nested
  class DeletePassport {

    @Test
    void testDeletePassportIfExists() {
      var savedAccount =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
      assertPresent(savedAccount.getId());
      passportDAO.insertPassport(
          TestUtils.createRandomPassport().withLinkedAccountId(savedAccount.getId()));

      assertPresent(
          passportDAO.getPassport(savedAccount.getUserId(), savedAccount.getProviderId()));
      assertTrue(passportDAO.deletePassport(savedAccount.getId().get()));
      assertEmpty(passportDAO.getPassport(savedAccount.getUserId(), savedAccount.getProviderId()));
    }

    @Test
    void testDeleteNonexistentPassport() {
      assertFalse(passportDAO.deletePassport(-1));
    }

    @Test
    void testAlsoDeletesVisa() {
      var savedAccountId =
          linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount()).getId();
      var savedPassport =
          passportDAO.insertPassport(
              TestUtils.createRandomPassport().withLinkedAccountId(savedAccountId));

      visaDAO.insertVisa(
          ImmutableGA4GHVisa.builder()
              .visaType("fake")
              .passportId(savedPassport.getId())
              .tokenType(TokenTypeEnum.access_token)
              .expires(new Timestamp(150))
              .issuer("fake-issuer")
              .lastValidated(new Timestamp(125))
              .jwt("fake-jwt")
              .build());

      assertFalse(visaDAO.listVisas(savedPassport.getId().get()).isEmpty());
      assertTrue(passportDAO.deletePassport(savedAccountId.get()));
      assertTrue(visaDAO.listVisas(savedPassport.getId().get()).isEmpty());
    }
  }
}

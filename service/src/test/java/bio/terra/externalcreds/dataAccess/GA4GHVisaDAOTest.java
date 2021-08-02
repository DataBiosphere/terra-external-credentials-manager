package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.ImmutableGA4GHPassport;
import bio.terra.externalcreds.models.ImmutableGA4GHVisa;
import bio.terra.externalcreds.models.ImmutableLinkedAccount;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

public class GA4GHVisaDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;
  @Autowired private GA4GHVisaDAO visaDAO;

  private final GA4GHVisa visa =
      ImmutableGA4GHVisa.builder()
          .visaType("fake")
          .expires(new Timestamp(100))
          .jwt("fake-jwt")
          .issuer("issuer")
          .tokenType(TokenTypeEnum.access_token)
          .lastValidated(new Timestamp(10))
          .build();

  /**
   * Insert a passport and linked account, which are required before the visa can be inserted.
   *
   * @return the ID of the saved passport.
   */
  private GA4GHPassport insertPassportAndLinkedAccount() {
    var linkedAccount =
        ImmutableLinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .externalUserId("externalUser")
            .build();
    var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    var passport =
        ImmutableGA4GHPassport.builder()
            .linkedAccountId(savedLinkedAccount.getId())
            .jwt("fake-jwt")
            .expires(new Timestamp(100))
            .build();
    var savedPassport = passportDAO.insertPassport(passport);

    return savedPassport;
  }

  @Test
  @Transactional
  @Rollback
  void testInsertAndListVisa() {
    var passport = insertPassportAndLinkedAccount();
    var expectedVisa1 = ImmutableGA4GHVisa.copyOf(visa).withPassportId(passport.getId());
    var expectedVisa2 =
        ImmutableGA4GHVisa.copyOf(visa)
            .withPassportId(passport.getId())
            .withTokenType(TokenTypeEnum.document_token);
    var savedVisa1 = visaDAO.insertVisa(expectedVisa1);
    var savedVisa2 = visaDAO.insertVisa(expectedVisa2);

    assertTrue(savedVisa1.getId().isPresent());
    assertTrue(savedVisa2.getId().isPresent());
    assertEquals(expectedVisa1, ImmutableGA4GHVisa.copyOf(savedVisa1).withId(Optional.empty()));
    assertEquals(expectedVisa2, ImmutableGA4GHVisa.copyOf(savedVisa2).withId(Optional.empty()));

    var loadedVisas = visaDAO.listVisas(passport.getId().get());
    assertEquals(loadedVisas.size(), 2);
    assertEquals(Set.of(savedVisa1, savedVisa2), Set.copyOf(loadedVisas));
  }

  @Test
  @Transactional
  @Rollback
  void testInsertInvalidForeignKey() {
    assertThrows(
        DataAccessException.class,
        () -> visaDAO.insertVisa(ImmutableGA4GHVisa.copyOf(visa).withPassportId(-1)));
  }

  @Test
  @Transactional
  @Rollback
  void testListNoVisas() {
    var loadedVisas = visaDAO.listVisas(-1);
    assertEquals(Collections.emptyList(), loadedVisas);
  }
}

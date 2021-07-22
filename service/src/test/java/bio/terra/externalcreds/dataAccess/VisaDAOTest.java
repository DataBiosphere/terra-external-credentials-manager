package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

public class VisaDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;
  @Autowired private GA4GHVisaDAO visaDAO;

  private GA4GHVisa visa;

  /**
   * Insert a passport and linked account, which are required before the visa can be inserted.
   *
   * @return the ID of the saved passport.
   */
  private int insertPassportAndLinkedAccount() {
    var linkedAccount =
        LinkedAccount.builder()
            .expires(new Timestamp(100))
            .providerId("provider")
            .refreshToken("refresh")
            .userId(UUID.randomUUID().toString())
            .externalUserId("externalUser")
            .build();
    var savedLinkedAccount = linkedAccountDAO.upsertLinkedAccount(linkedAccount);
    var passport =
        GA4GHPassport.builder()
            .linkedAccountId(savedLinkedAccount.getId())
            .jwt("fake-jwt")
            .expires(new Timestamp(100))
            .build();
    var savedPassport = passportDAO.insertPassport(passport);

    return savedPassport.getId();
  }

  @BeforeEach
  void setup() {
    visa =
        GA4GHVisa.builder()
            .visaType("fake")
            .expires(new Timestamp(100))
            .jwt("fake-jwt")
            .issuer("issuer")
            .tokenType(TokenTypeEnum.access_token)
            .lastValidated(new Timestamp(10))
            .build();
  }

  @Test
  @Transactional
  @Rollback
  void testInsertAndListVisa() {
    var passportId = insertPassportAndLinkedAccount();
    var expectedVisa1 = visa.withPassportId(passportId);
    var expectedVisa2 = visa.withPassportId(passportId).withTokenType(TokenTypeEnum.document_token);
    var savedVisa1 = visaDAO.insertVisa(expectedVisa1);
    var savedVisa2 = visaDAO.insertVisa(expectedVisa2);

    assertTrue(savedVisa1.getId() > 0);
    assertTrue(savedVisa2.getId() > 0);
    assertEquals(expectedVisa1, savedVisa1.withId(0));
    assertEquals(expectedVisa2, savedVisa2.withId(0));

    var loadedVisas = visaDAO.listVisas(passportId);
    assertEquals(loadedVisas.size(), 2);
    assertEquals(savedVisa1, loadedVisas.get(0));
    assertEquals(savedVisa2, loadedVisas.get(1));
  }

  @Test
  @Transactional
  @Rollback
  void testInsertInvalidForeignKey() {
    assertThrows(DataAccessException.class, () -> visaDAO.insertVisa(visa.withPassportId(-1)));
  }

  @Test
  @Transactional
  @Rollback
  void testListNoVisas() {
    var loadedVisas = visaDAO.listVisas(-1);
    assertEquals(Collections.emptyList(), loadedVisas);
  }
}

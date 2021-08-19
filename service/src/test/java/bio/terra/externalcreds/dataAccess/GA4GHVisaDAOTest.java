package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.models.ImmutableGA4GHVisa;
import bio.terra.externalcreds.models.TokenTypeEnum;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

public class GA4GHVisaDAOTest extends BaseTest {

  @Autowired private LinkedAccountDAO linkedAccountDAO;
  @Autowired private GA4GHPassportDAO passportDAO;
  @Autowired private GA4GHVisaDAO visaDAO;

  @Test
  void testInsertAndListVisa() {
    var savedLinkedAccount =
        linkedAccountDAO.upsertLinkedAccount(TestUtils.createRandomLinkedAccount());
    var savedPassport =
        passportDAO.insertPassport(
            TestUtils.createRandomPassport().withLinkedAccountId(savedLinkedAccount.getId()));

    var immutableVisa = TestUtils.createRandomVisa();
    var expectedVisa1 = immutableVisa.withPassportId(savedPassport.getId());
    var expectedVisa2 =
        immutableVisa
            .withPassportId(savedPassport.getId())
            .withTokenType(TokenTypeEnum.document_token);
    var savedVisa1 = visaDAO.insertVisa(expectedVisa1);
    var savedVisa2 = visaDAO.insertVisa(expectedVisa2);

    assertTrue(savedVisa1.getId().isPresent());
    assertTrue(savedVisa2.getId().isPresent());
    assertEquals(expectedVisa1, ImmutableGA4GHVisa.copyOf(savedVisa1).withId(Optional.empty()));
    assertEquals(expectedVisa2, ImmutableGA4GHVisa.copyOf(savedVisa2).withId(Optional.empty()));

    var loadedVisas = visaDAO.listVisas(savedPassport.getId().get());
    assertEquals(loadedVisas.size(), 2);
    assertEquals(Set.of(savedVisa1, savedVisa2), Set.copyOf(loadedVisas));
  }

  @Test
  void testInsertVisaWithInvalidForeignKey() {
    assertThrows(
        DataAccessException.class,
        () -> visaDAO.insertVisa(TestUtils.createRandomVisa().withPassportId(-1)));
  }

  @Test
  void testListNoVisas() {
    var loadedVisas = visaDAO.listVisas(-1);
    assertEquals(Collections.emptyList(), loadedVisas);
  }
}

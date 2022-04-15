package bio.terra.externalcreds.dataAccess;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.models.OAuth2State;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class OAuth2StateDAOTest extends BaseTest {
  @Autowired private OAuth2StateDAO oAuth2StateDAO;

  @Test
  void testCreateAndDelete() {
    var provider = "provider name";
    var userId = "user";
    var redirectUri = "https://foo";
    var firstState =
        new OAuth2State.Builder()
            .provider(provider)
            .random(OAuth2State.generateRandomState(new SecureRandom()))
            .redirectUri(redirectUri)
            .build();
    var secondState =
        new OAuth2State.Builder()
            .provider(provider)
            .random(OAuth2State.generateRandomState(new SecureRandom()))
            .redirectUri(redirectUri)
            .build();

    oAuth2StateDAO.upsertOidcState(userId, firstState);
    oAuth2StateDAO.upsertOidcState(userId, secondState);

    // firstState should have been overwritten
    assertFalse(oAuth2StateDAO.deleteOidcStateIfExists(userId, firstState));

    // secondState should exist
    assertTrue(oAuth2StateDAO.deleteOidcStateIfExists(userId, secondState));

    // secondState should have been deleted
    assertFalse(oAuth2StateDAO.deleteOidcStateIfExists(userId, secondState));
  }
}

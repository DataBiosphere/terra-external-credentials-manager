package bio.terra.externalcreds.service;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.services.OAuth2Service;
import bio.terra.externalcreds.services.ProviderClientCache;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

public class OAuth2ServiceTest extends BaseTest {

  @Autowired private ProviderConfig providerConfig;
  @Autowired private OAuth2Service oAuth2Service;
  @Autowired private ProviderClientCache providerClientCache;

  @Test
  void test() {
    ClientRegistration providerClient = providerClientCache.getProviderClient("ras");
    OAuth2AccessTokenResponse tokenResponse =
        oAuth2Service.authorizeWithRefreshToken(
            providerClient,
            new OAuth2RefreshToken("36e515d8-4169-4a8f-86d1-57116cfa6abe", Instant.now()));

    System.out.println(tokenResponse.getAccessToken().getTokenValue());
  }
}

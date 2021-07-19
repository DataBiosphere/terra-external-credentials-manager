package bio.terra.externalcreds.services;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

public class ProviderServiceTest extends BaseTest {

    @MockBean OAuth2Service oAuth2Service;
    @MockBean ProviderClientCache providerClientCache;
    @MockBean ProviderConfig providerConfig;

    @Test
    @Transactional
    @Rollback
    void testUseAuthorizationCodeToGetLinkedAccount() {
        String provider;
        String userId;
        String authorizationCode;
        String redirectUri;
        Set<String> scopes;
        String stat;


    }
}

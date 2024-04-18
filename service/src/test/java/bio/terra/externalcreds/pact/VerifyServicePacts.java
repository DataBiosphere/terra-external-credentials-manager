package bio.terra.externalcreds.pact;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerConsumerVersionSelectors;
import au.com.dius.pact.provider.junitsupport.loader.SelectorBuilder;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.SamUser;
import bio.terra.common.iam.SamUserFactory;
import bio.terra.externalcreds.ExternalCredsWebApplication;
import bio.terra.externalcreds.dataAccess.AccessTokenCacheDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.dataAccess.StatusDAO;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.AccessTokenCacheEntry;
import bio.terra.externalcreds.models.ImmutableLinkedAccount;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.KmsEncryptDecryptHelper;
import bio.terra.externalcreds.services.OAuth2Service;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.util.StringUtils;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {"server.port=8080"},
    classes = ExternalCredsWebApplication.class)
@ActiveProfiles({"test", "human-readable-logging"})
@au.com.dius.pact.provider.junitsupport.Provider("externalcreds")
@PactBroker
public class VerifyServicePacts {
  private static final String CONSUMER_BRANCH = System.getenv("CONSUMER_BRANCH");

  @PactBrokerConsumerVersionSelectors
  public static SelectorBuilder consumerVersionSelectors() {
    // The following match condition basically says
    // If verification is triggered by Pact Broker webhook due to consumer pact change, verify only
    // the changed pact.
    // Otherwise, this is a PR, verify all consumer pacts in Pact Broker marked with a deployment
    // tag (e.g. dev, alpha).
    if (StringUtils.isBlank(CONSUMER_BRANCH)) {
      return new SelectorBuilder().mainBranch().deployedOrReleased();
    } else {
      return new SelectorBuilder().branch(CONSUMER_BRANCH);
    }
  }

  @MockBean StatusDAO statusDAO;
  @MockBean LinkedAccountDAO linkedAccountDAO;
  @MockBean AccessTokenCacheDAO accessTokenCacheDAO;
  @MockBean SamUserFactory samUserFactory;
  @MockBean OAuth2Service oAuth2Service;
  @Mock private OAuth2AccessTokenResponse mockAccessTokenResponse;

  // KmsEncryptDecryptHelper is being mocked out of convenience, because leaving it unmocked
  // causes bio.terra.externalcreds.ExternalCredsException at KmsEncryptDecryptHelper.java:40.
  @MockBean KmsEncryptDecryptHelper kmsEncryptDecryptHelper;

  @BeforeEach
  void setupTestTarget(PactVerificationContext context) {
    context.setTarget(new HttpTestTarget("localhost", 8080, "/"));
  }

  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void pactVerificationTestTemplate(PactVerificationContext context) {
    context.verifyInteraction();
  }

  @State({ProviderStates.ECM_IS_OK})
  public void ecmIsOk() {
    when(statusDAO.isPostgresOk()).thenReturn(true);
  }

  @State({ProviderStates.USER_IS_REGISTERED})
  public Map<String, String> userIsRegistered() {
    String testUserEmail = "test_user@test.com";
    String testSubjectId = "testSubjectId";
    String testBearerToken = "testBearerToken";

    OAuth2RefreshToken testRefreshToken =
        new OAuth2RefreshToken("dummy_refresh_token", Instant.now());
    OAuth2AccessToken testAccessToken =
        new OAuth2AccessToken(
            TokenType.BEARER, testBearerToken, Instant.now(), Instant.now().plusSeconds(360));

    when(samUserFactory.from(isA(HttpServletRequest.class), anyString()))
        .thenReturn(new SamUser(testUserEmail, testSubjectId, new BearerToken(testBearerToken)));

    ImmutableLinkedAccount testAccount =
        new LinkedAccount.Builder()
            .id(0)
            .userId(testUserEmail)
            .provider(Provider.GITHUB)
            .refreshToken(testRefreshToken.getTokenValue())
            .expires(Timestamp.from(Instant.now().plusSeconds(360)))
            .externalUserId("dummy external_user_id")
            .isAuthenticated(true)
            .build();

    AccessTokenCacheEntry accessTokenCacheEntry =
        new AccessTokenCacheEntry.Builder()
            .linkedAccountId(0)
            .accessToken(testAccessToken.getTokenValue())
            .expiresAt(Instant.now())
            .build();

    when(linkedAccountDAO.getLinkedAccount(eq(testSubjectId), eq(Provider.GITHUB)))
        .thenReturn(Optional.of(testAccount));

    when(accessTokenCacheDAO.upsertAccessTokenCacheEntry(isA(AccessTokenCacheEntry.class)))
        .thenReturn(accessTokenCacheEntry);

    when(mockAccessTokenResponse.getRefreshToken()).thenReturn(testRefreshToken);
    when(mockAccessTokenResponse.getAccessToken()).thenReturn(testAccessToken);

    when(oAuth2Service.authorizeWithRefreshToken(isA(ClientRegistration.class), any(), any()))
        .thenReturn(mockAccessTokenResponse);

    // These values are returned so that they can be injected into variables in the Pact(s)
    return Map.of(
        "userEmail", testUserEmail,
        "bearerToken", testBearerToken);
  }
}

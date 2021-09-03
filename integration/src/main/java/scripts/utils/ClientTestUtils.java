package scripts.utils;

import bio.terra.externalcreds.client.ApiClient;
import bio.terra.testrunner.common.utils.AuthenticationUtils;
import bio.terra.testrunner.runner.config.ServerSpecification;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;

public class ClientTestUtils {

  private ClientTestUtils() {}

  // Required scopes for client tests include the usual login scopes and GCP scope.
  private static final List<String> TEST_USER_SCOPES =
      List.of("openid", "email", "profile", "https://www.googleapis.com/auth/cloud-platform");

  /**
   * Build the ECM API client object for the server specifications. No access token is needed for
   * this API client.
   *
   * @param server the server we are testing against
   * @return the API client object for this user
   */
  public static ApiClient getClientWithoutAccessToken(ServerSpecification server)
      throws IOException {
    return buildClient(null, server);
  }

  private static ApiClient buildClient(
      @Nullable AccessToken accessToken, ServerSpecification server) throws IOException {
    if (Strings.isNullOrEmpty(server.policyManagerUri)) {
      throw new IllegalArgumentException("Service URI cannot be empty");
    }

    // build the client object
    ApiClient apiClient = new ApiClient();
    apiClient.setBasePath(server.policyManagerUri);

    if (accessToken != null) {
      apiClient.setAccessToken(accessToken.getTokenValue());
    }

    return apiClient;
  }

  /**
   * Build an ECM API client object for the given test user and server specifications. The test
   * user's token is always refreshed
   *
   * @param testUser the test user whose credentials are supplied to the API client object
   * @param server the server we are testing against
   * @return the API client object for this user
   */
  public static ApiClient getClientWithAuthenticatedUser(
      TestUserSpecification testUser, ServerSpecification server) throws IOException {
    GoogleCredentials userCredential =
        AuthenticationUtils.getDelegatedUserCredential(testUser, TEST_USER_SCOPES);
    var accessToken = AuthenticationUtils.getAccessToken(userCredential);

    return buildClient(accessToken, server);
  }
}

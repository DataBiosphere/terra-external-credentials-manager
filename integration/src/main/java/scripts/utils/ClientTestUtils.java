package scripts.utils;

import bio.terra.externalcreds.client.ApiClient;
import bio.terra.testrunner.runner.config.ServerSpecification;
import com.google.auth.oauth2.AccessToken;
import com.google.common.base.Strings;

import javax.annotation.Nullable;
import java.io.IOException;

public class ClientTestUtils {

    private ClientTestUtils() {}

    /**
     * Build the ECM API client object for the server specifications. No access token is
     * needed for this API client.
     *
     * @param server the server we are testing against
     * @return the API client object for this user
     */
    public static ApiClient getClientWithoutAccessToken(ServerSpecification server)
            throws IOException {
        return buildClient(null, server);
    }

    private static ApiClient buildClient(@Nullable AccessToken accessToken, ServerSpecification server) throws IOException {
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
}

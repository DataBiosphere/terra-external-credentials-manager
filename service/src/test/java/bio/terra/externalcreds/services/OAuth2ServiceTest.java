package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ProviderConfig;
import java.util.Scanner;
import java.util.Set;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.stereotype.Component;

@SpringBootApplication(scanBasePackages = {"bio.terra.externalcreds"})
@Component
public class OAuth2ServiceTest {

  @Autowired private ProviderConfig providerConfig;
  @Autowired private OAuth2Service oAuth2Service;
  @Autowired private ProviderClientCache providerClientCache;

  public static void main(String[] args) {
    new SpringApplicationBuilder(OAuth2ServiceTest.class)
        .profiles("human-readable-logging")
        .run(args)
        .getBean(OAuth2ServiceTest.class)
        .test();
  }

  /**
   * Test to run through the basics of the oauth flow. Must have local configs rendered. This is its
   * own application instead of a standard unit test because it requires user interaction to login
   * to the identity provider.
   */
  void test() {
    val providerClient = providerClientCache.getProviderClient("ras");

    val redirectUri = "http://localhost:9000/fence-callback";
    val scopes = Set.of("openid", "email", "ga4gh_passport_v1");
    String state = null;
    val authorizationParameters =
        providerConfig.getServices().get("ras").getAdditionalAuthorizationParameters();

    // 1) test getAuthorizationRequestUri
    val authorizationRequestUri =
        oAuth2Service.getAuthorizationRequestUri(
            providerClient, redirectUri, scopes, state, authorizationParameters);

    System.out.println(
        "Open following url, after login the browser will be redirected to a url that does not exist, copy the 'code' parameter from the URL and paste below");
    System.out.println(authorizationRequestUri);
    System.out.print("Enter authorization code: ");
    val authCode = new Scanner(System.in).nextLine();

    // 2) test authorizationCodeExchange
    val oAuth2AccessTokenResponse =
        oAuth2Service.authorizationCodeExchange(
            providerClient, authCode, redirectUri, scopes, state, authorizationParameters);

    // 3) test authorizeWithRefreshToken
    // note that oAuth2AccessTokenResponse already has an access token but get another for testing
    val tokenResponse =
        oAuth2Service.authorizeWithRefreshToken(
            providerClient, oAuth2AccessTokenResponse.getRefreshToken());

    // 4) test getUserInfo
    val oAuth2User = oAuth2Service.getUserInfo(providerClient, tokenResponse.getAccessToken());

    // oAuth2User should have the user's passport and email address in the attributes
    System.out.println(oAuth2User.toString());
  }
}

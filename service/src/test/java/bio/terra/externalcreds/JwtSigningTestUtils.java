package bio.terra.externalcreds;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.util.Map;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

public class JwtSigningTestUtils {
  public static final String JWKS_PATH = "/openid/connect/jwks.json";
  public static final String JKU_PATH = "/jku.json";

  public ClientAndServer mockServer;
  public RSAKey accessTokenRsaJWK;
  public JWSSigner accessTokenSigner;
  public RSAKey documentTokenRsaJWK;
  public JWSSigner documentTokenSigner;
  public String issuer;

  public void setUpJwtVerification() throws JOSEException {
    accessTokenRsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();
    documentTokenRsaJWK = new RSAKeyGenerator(2048).keyID("456").generate();

    // Create RSA-signer with the private key
    accessTokenSigner = new RSASSASigner(accessTokenRsaJWK);
    documentTokenSigner = new RSASSASigner(documentTokenRsaJWK);

    mockServer = ClientAndServer.startClientAndServer(50555);

    issuer = "http://localhost:" + mockServer.getPort();
    var wellKnownConfigMap = Map.of("issuer", issuer, "jwks_uri", issuer + JWKS_PATH);

    mockServer
        .when(HttpRequest.request("/.well-known/openid-configuration").withMethod("GET"))
        .respond(
            HttpResponse.response(JSONObjectUtils.toJSONString(wellKnownConfigMap))
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON));

    mockServer
        .when(HttpRequest.request(JWKS_PATH).withMethod("GET"))
        .respond(
            HttpResponse.response(new JWKSet(accessTokenRsaJWK).toString())
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON));

    mockServer
        .when(HttpRequest.request(JKU_PATH).withMethod("GET"))
        .respond(
            HttpResponse.response(new JWKSet(documentTokenRsaJWK).toString())
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON));
  }

  public void tearDown() {
    mockServer.stop();
  }
}

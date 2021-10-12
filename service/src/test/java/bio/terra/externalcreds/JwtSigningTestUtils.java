package bio.terra.externalcreds;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader.Builder;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.util.Map;
import lombok.SneakyThrows;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

public class JwtSigningTestUtils {
  public static final String JWKS_PATH = "/openid/connect/jwks.json";
  public static final String JKU_PATH = "/jku.json";

  private ClientAndServer mockServer;
  private RSAKey accessTokenRsaJWK;
  private JWSSigner accessTokenSigner;
  private RSAKey documentTokenRsaJWK;
  private JWSSigner documentTokenSigner;
  private String issuer;

  public void setUpJwtVerification() throws JOSEException {
    accessTokenRsaJWK = new RSAKeyGenerator(2048).keyID("123").generate();
    documentTokenRsaJWK = new RSAKeyGenerator(2048).keyID("456").generate();

    // Create RSA-signer with the private key
    accessTokenSigner = new RSASSASigner(accessTokenRsaJWK);
    documentTokenSigner = new RSASSASigner(documentTokenRsaJWK);

    mockServer = ClientAndServer.startClientAndServer();

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

  @SneakyThrows
  public String createSignedJwt(JWTClaimsSet claimsSet) {
    var jwtHeader = new Builder(JWSAlgorithm.RS256).keyID(accessTokenRsaJWK.getKeyID()).build();
    var signedVisaJwt = new SignedJWT(jwtHeader, claimsSet);
    signedVisaJwt.sign(accessTokenSigner);
    return signedVisaJwt.serialize();
  }

  @SneakyThrows
  public String createSignedDocumentTokenJwt(JWTClaimsSet claimsSet, String issuer) {
    var jwtHeader =
        new Builder(JWSAlgorithm.RS256)
            .jwkURL(new URI(issuer + JwtSigningTestUtils.JKU_PATH))
            .keyID(documentTokenRsaJWK.getKeyID())
            .build();

    var signedVisaJwt = new SignedJWT(jwtHeader, claimsSet);
    signedVisaJwt.sign(documentTokenSigner);
    return signedVisaJwt.serialize();
  }

  public String getIssuer() {
    return issuer;
  }
}

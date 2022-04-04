package bio.terra.externalcreds;

import bio.terra.externalcreds.models.GA4GHPassport;
import bio.terra.externalcreds.models.GA4GHVisa;
import bio.terra.externalcreds.models.TokenTypeEnum;
import bio.terra.externalcreds.services.JwtUtils;
import bio.terra.externalcreds.services.ProviderService;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

public class JwtSigningTestUtils {
  public static final String JWKS_PATH = "/openid/connect/jwks.json";
  public static final String JKU_PATH = "/jku.json";

  // Round the expiration to the nearest second because it will be rounded in the JWT.
  public final Date passportExpires = new Date((new Date().getTime() + 60 * 1000) / 1000 * 1000);
  public final Timestamp passportExpiresTime = new Timestamp(passportExpires.getTime());

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
    var jwtHeader =
        new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(accessTokenRsaJWK.getKeyID()).build();
    var signedVisaJwt = new SignedJWT(jwtHeader, claimsSet);
    signedVisaJwt.sign(accessTokenSigner);
    return signedVisaJwt.serialize();
  }

  @SneakyThrows
  public String createSignedDocumentTokenJwt(JWTClaimsSet claimsSet, String issuer) {
    var jwtHeader =
        new JWSHeader.Builder(JWSAlgorithm.RS256)
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

  public String createVisaJwtString(GA4GHVisa visa) {
    return createVisaJwtStringWithClaims(visa, Map.of());
  }

  public String createVisaJwtStringWithClaims(GA4GHVisa visa, Map<String, Object> claims) {
    var visaClaimSetBuilder =
        new JWTClaimsSet.Builder()
            .expirationTime(visa.getExpires())
            .issuer(visa.getIssuer().equals("null") ? null : visa.getIssuer())
            .claim(
                JwtUtils.GA4GH_VISA_V1_CLAIM, Map.of(JwtUtils.VISA_TYPE_CLAIM, visa.getVisaType()));

    claims.forEach(visaClaimSetBuilder::claim);

    var visaClaimSet = visaClaimSetBuilder.build();

    return switch (visa.getTokenType()) {
      case access_token -> createSignedJwt(visaClaimSet);
      case document_token -> createSignedDocumentTokenJwt(visaClaimSet, visa.getIssuer());
    };
  }

  public GA4GHVisa createTestVisaWithJwt(TokenTypeEnum tokenType) {
    return createTestVisaWithJwtWithClaims(tokenType, Map.of(), UUID.randomUUID().toString());
  }

  public GA4GHVisa createTestVisaWithJwtWithClaims(
      TokenTypeEnum tokenType, Map<String, Object> claims, String visaType) {
    var visa =
        new GA4GHVisa.Builder()
            .visaType(visaType)
            .tokenType(tokenType)
            .issuer(getIssuer())
            .expires(passportExpiresTime)
            .jwt("temp")
            .build();
    visa = visa.withJwt(createVisaJwtStringWithClaims(visa, claims));
    return visa;
  }

  public GA4GHPassport createTestPassport(List<GA4GHVisa> visas, String userEmail) {
    var visaJwts = visas.stream().map(GA4GHVisa::getJwt).toList();
    var jwtId = UUID.randomUUID().toString();
    var jwtString = createPassportJwtString(passportExpires, visaJwts, jwtId, userEmail);
    return new GA4GHPassport.Builder()
        .jwt(jwtString)
        .expires(passportExpiresTime)
        .jwtId(jwtId)
        .build();
  }

  private String createPassportJwtString(
      Date expires, List<String> visaJwts, String jwtId, String userEmail) {

    var passportClaimSetBuilder =
        new JWTClaimsSet.Builder()
            .subject(UUID.randomUUID().toString())
            .claim(ProviderService.EXTERNAL_USERID_ATTR, userEmail)
            .issuer(getIssuer())
            .jwtID(jwtId)
            .expirationTime(expires);

    if (!visaJwts.isEmpty()) {
      passportClaimSetBuilder.claim(JwtUtils.GA4GH_PASSPORT_V1_CLAIM, visaJwts);
    }

    var claimsSet = passportClaimSetBuilder.build();
    return createSignedJwt(claimsSet);
  }
}

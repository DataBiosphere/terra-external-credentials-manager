package bio.terra.externalcreds.services;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.Assert;

/** provides decoders that are not in {@link JwtDecoders} */
public class ExternalCredsJwtDecoders {
  private ExternalCredsJwtDecoders() {}

  /** Adapted from {@link JwtDecoders#withProviderConfiguration(java.util.Map, String)} */
  public static JwtDecoder fromJku(URI jku) throws MalformedURLException {
    OAuth2TokenValidator<Jwt> jwtValidator = JwtValidators.createDefault();
    RemoteJWKSet<SecurityContext> jwkSource = new RemoteJWKSet<>(jku.toURL());
    Set<SignatureAlgorithm> signatureAlgorithms = getSignatureAlgorithms(jwkSource);
    NimbusJwtDecoder jwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(jku.toString())
            .jwsAlgorithms((algs) -> algs.addAll(signatureAlgorithms))
            .build();
    jwtDecoder.setJwtValidator(jwtValidator);
    return jwtDecoder;
  }

  /**
   * Copied from {@link
   * org.springframework.security.oauth2.jwt.JwtDecoderProviderConfigurationUtils#getSignatureAlgorithms(JWKSource)}
   * because it is private
   */
  private static Set<SignatureAlgorithm> getSignatureAlgorithms(
      JWKSource<SecurityContext> jwkSource) {
    JWKMatcher jwkMatcher =
        new JWKMatcher.Builder()
            .publicOnly(true)
            .keyUses(KeyUse.SIGNATURE, null)
            .keyTypes(KeyType.RSA, KeyType.EC)
            .build();
    Set<JWSAlgorithm> jwsAlgorithms = new HashSet<>();
    try {
      List<? extends JWK> jwks = jwkSource.get(new JWKSelector(jwkMatcher), null);
      for (JWK jwk : jwks) {
        if (jwk.getAlgorithm() != null) {
          JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jwk.getAlgorithm().getName());
          jwsAlgorithms.add(jwsAlgorithm);
        } else {
          if (jwk.getKeyType() == KeyType.RSA) {
            jwsAlgorithms.addAll(JWSAlgorithm.Family.RSA);
          } else if (jwk.getKeyType() == KeyType.EC) {
            jwsAlgorithms.addAll(JWSAlgorithm.Family.EC);
          }
        }
      }
    } catch (KeySourceException ex) {
      throw new IllegalStateException(ex);
    }
    Set<SignatureAlgorithm> signatureAlgorithms = new HashSet<>();
    for (JWSAlgorithm jwsAlgorithm : jwsAlgorithms) {
      SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.from(jwsAlgorithm.getName());
      if (signatureAlgorithm != null) {
        signatureAlgorithms.add(signatureAlgorithm);
      }
    }
    Assert.notEmpty(signatureAlgorithms, "Failed to find any algorithms from the JWK set");
    return signatureAlgorithms;
  }
}

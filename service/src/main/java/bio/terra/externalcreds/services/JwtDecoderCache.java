package bio.terra.externalcreds.services;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.stereotype.Component;

/**
 * Creating a jwt decoder requires external calls to get public keys. That information almost never
 * changes and we don't want to hammer that api. Therefore this cache.
 *
 * <p>The cache is reset every 6 hours to detect infrequent changes.
 */
@Component
@Slf4j
public class JwtDecoderCache {
  @Cacheable(cacheNames = "jwtDecodersFromIssuer")
  public JwtDecoder fromIssuer(String issuer) {
    log.info("Loading JwtDecoder from issuer {}", issuer);
    return JwtDecoders.fromIssuerLocation(issuer);
  }

  @Cacheable(cacheNames = "jwtDecodersFromJku")
  public JwtDecoder fromJku(URI jku) {
    try {
      log.info("Loading JwtDecoder from jku {}", jku);
      return ExternalCredsJwtDecoders.fromJku(jku);
    } catch (MalformedURLException e) {
      throw new InvalidJwtException(e);
    }
  }

  @Scheduled(fixedRateString = "6", timeUnit = TimeUnit.HOURS)
  @CacheEvict(
      allEntries = true,
      cacheNames = {"jwtDecodersFromIssuer", "jwtDecodersFromJku"})
  public void resetCache() {
    log.info("JwtDecoderCache reset");
  }
}

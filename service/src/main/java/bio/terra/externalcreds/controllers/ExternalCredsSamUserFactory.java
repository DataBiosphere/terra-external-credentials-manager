package bio.terra.externalcreds.controllers;

import bio.terra.common.iam.BearerTokenFactory;
import bio.terra.common.iam.SamUser;
import bio.terra.common.iam.SamUserFactory;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.stereotype.Component;

@Component
public class ExternalCredsSamUserFactory {

  private SamUserFactory samUserFactory;
  private ExternalCredsConfig externalCredsConfig;

  private BearerTokenFactory bearerTokenFactory;

  // This is per-pod cache, not shared across pods.
  // This is just to keep ECM from hitting Sam too much given the bursty loads ECM handles.
  private final Map<Integer, SamUser> samUserCache =
      Collections.synchronizedMap(new PassiveExpiringMap<>(1, TimeUnit.MINUTES));

  public ExternalCredsSamUserFactory(
      SamUserFactory samUserFactory,
      BearerTokenFactory bearerTokenFactory,
      ExternalCredsConfig externalCredsConfig) {
    this.samUserFactory = samUserFactory;
    this.bearerTokenFactory = bearerTokenFactory;
    this.externalCredsConfig = externalCredsConfig;
  }

  public SamUser from(HttpServletRequest request) {
    var bearerToken = bearerTokenFactory.from(request);
    return samUserCache.computeIfAbsent(
        bearerToken.hashCode(),
        hash -> samUserFactory.from(request, externalCredsConfig.getSamBasePath()));
  }
}

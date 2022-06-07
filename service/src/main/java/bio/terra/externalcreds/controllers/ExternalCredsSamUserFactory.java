package bio.terra.externalcreds.controllers;

import bio.terra.common.iam.SamUser;
import bio.terra.common.iam.SamUserFactory;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public record ExternalCredsSamUserFactory(
    SamUserFactory samUserFactory, ExternalCredsConfig externalCredsConfig) {

  public SamUser from(HttpServletRequest request) {
    return samUserFactory.from(request, externalCredsConfig.getSamBasePath());
  }
}

package bio.terra.externalcreds.controllers;

import static bio.terra.externalcreds.generated.model.Provider.ERA_COMMONS;

import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.api.OauthApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record OauthApiController(
    AuditLogger auditLogger,
    HttpServletRequest request,
    ObjectMapper mapper,
    LinkedAccountService linkedAccountService,
    ProviderService providerService,
    ExternalCredsSamUserFactory samUserFactory,
    ExternalCredsConfig externalCredsConfig)
    implements OauthApi {

  @Override
  public ResponseEntity<List<String>> listProviders() {
    var providerNames = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providerNames);

    return ResponseEntity.ok(providerNames);
  }

  @Override
  public ResponseEntity<LinkInfo> getLink(Provider provider) {
    var samUser = samUserFactory.from(request);
    var linkedAccount = linkedAccountService.getLinkedAccount(samUser.getSubjectId(), provider);
    return ResponseEntity.of(linkedAccount.map(OpenApiConverters.Output::convert));
  }

  @Override
  public ResponseEntity<String> getAuthorizationUrl(Provider provider, String redirectUri) {
    var samUser = samUserFactory.from(request);

    var authorizationUrl =
        providerService.getProviderAuthorizationUrl(
            samUser.getSubjectId(), provider, redirectUri, null);

    return ResponseEntity.ok(authorizationUrl);
  }

  @Override
  public ResponseEntity<String> getAuthorizationUrlWithAdditionalParams(
      String redirectUri, Provider provider, Map<String, String> body) {
    var samUser = samUserFactory.from(request);

    var authorizationUrl =
        providerService.getProviderAuthorizationUrl(
            samUser.getSubjectId(), provider, redirectUri, body);

    return ResponseEntity.ok(authorizationUrl);
  }

  @Override
  public ResponseEntity<String> getProviderAccessToken(Provider provider) {
    var samUser = samUserFactory.from(request);

    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .provider(provider)
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr());

    var accessToken =
        providerService.getProviderAccessToken(
            samUser.getSubjectId(), provider, auditLogEventBuilder);
    return ResponseEntity.ok(accessToken);
  }

  @Override
  public ResponseEntity<LinkInfo> createLink(Provider provider, String state, String oauthcode) {
    if (!externalCredsConfig.getEraCommonsLinkingEnabled() && provider.equals(ERA_COMMONS)) {
      throw new UnsupportedOperationException(
          "eRA Commons is not supported for link creation (yet)");
    }

    var samUser = samUserFactory.from(request);

    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .provider(provider)
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr());

    try {

      var linkedAccountWithPassportAndVisas =
          providerService.createLink(
              provider, samUser.getSubjectId(), oauthcode, state, auditLogEventBuilder);
      LinkInfo linkInfo =
          OpenApiConverters.Output.convert(linkedAccountWithPassportAndVisas.getLinkedAccount());

      Optional<Map<String, String>> additionalState =
          providerService.getAdditionalStateParams(state);
      additionalState.ifPresent(linkInfo::additionalState);
      return ResponseEntity.ok(linkInfo);
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLogEventBuilder.auditLogEventType(AuditLogEventType.LinkCreationFailed).build());
      throw e;
    }
  }

  @Override
  public ResponseEntity<Void> deleteLink(Provider provider) {
    var samUser = samUserFactory.from(request);
    var deletedLink = providerService.deleteLink(samUser.getSubjectId(), provider);

    auditLogger.logEvent(
        new AuditLogEvent.Builder()
            .auditLogEventType(AuditLogEventType.LinkDeleted)
            .provider(provider)
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr())
            .externalUserId(deletedLink.getExternalUserId())
            .build());

    return ResponseEntity.ok().build();
  }
}

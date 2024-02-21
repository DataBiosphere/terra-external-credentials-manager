package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.controllers.OpenApiConverters.Output;
import bio.terra.externalcreds.generated.api.OauthApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record OauthApiController(
    AuditLogger auditLogger,
    HttpServletRequest request,
    ObjectMapper mapper,
    ProviderService providerService,
    PassportProviderService passportProviderService,
    TokenProviderService tokenProviderService,
    ExternalCredsSamUserFactory samUserFactory)
    implements OauthApi {

  @Override
  public ResponseEntity<String> getAuthorizationUrl(Provider providerName, String redirectUri) {
    var samUser = samUserFactory.from(request);

    var authorizationUrl =
        providerService.getProviderAuthorizationUrl(
            samUser.getSubjectId(), providerName.toString(), redirectUri);

    return ResponseEntity.of(authorizationUrl);
  }

  @Override
  public ResponseEntity<LinkInfo> createLink(
      Provider providerName, String state, String oauthcode) {
    var samUser = samUserFactory.from(request);

    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .providerName(providerName.toString())
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr());

    Optional<LinkInfo> linkInfo = Optional.empty();
    try {
      switch (providerName) {
        case RAS -> {
          var linkedAccountWithPassportAndVisas =
              passportProviderService.createLink(
                  providerName.toString(),
                  samUser.getSubjectId(),
                  oauthcode,
                  state,
                  auditLogEventBuilder);
          linkInfo =
              linkedAccountWithPassportAndVisas.map(
                  x -> OpenApiConverters.Output.convert(x.getLinkedAccount()));
        }
        case GITHUB -> {
          var linkedAccount =
              tokenProviderService.createLink(
                  providerName.toString(),
                  samUser.getSubjectId(),
                  oauthcode,
                  state,
                  auditLogEventBuilder);
          linkInfo = linkedAccount.map(Output::convert);
        }
      }
      return ResponseEntity.of(linkInfo);
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLogEventBuilder.auditLogEventType(AuditLogEventType.LinkCreationFailed).build());
      throw e;
    }
  }
}

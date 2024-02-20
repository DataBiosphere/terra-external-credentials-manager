package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.api.OauthApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.services.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record OauthApiController(
    AuditLogger auditLogger,
    HttpServletRequest request,
    ObjectMapper mapper,
    ProviderService providerService,
    PassportProviderService passportProviderService,
    ExternalCredsSamUserFactory samUserFactory)
    implements OauthApi {

  @Override
  public ResponseEntity<String> getAuthUrl(Provider providerName, String redirectUri) {
    var samUser = samUserFactory.from(request);

    var authorizationUrl =
        providerService.getProviderAuthorizationUrl(
            samUser.getSubjectId(), providerName.toString(), redirectUri);

    return ResponseEntity.of(authorizationUrl.map(this::jsonString));
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

    try {
      if (providerName.equals(Provider.RAS)) {
        var linkedAccountWithPassportAndVisas =
            passportProviderService.createLink(
                providerName.toString(),
                samUser.getSubjectId(),
                oauthcode,
                state,
                auditLogEventBuilder);
        return ResponseEntity.of(
            linkedAccountWithPassportAndVisas.map(
                x -> OpenApiConverters.Output.convert(x.getLinkedAccount())));
      } else {
        throw new BadRequestException("Invalid providerName");
      }
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLogEventBuilder.auditLogEventType(AuditLogEventType.LinkCreationFailed).build());
      throw e;
    }
  }

  /** Helper method to format a string as json. Otherwise it isn't quoted or escaped correctly. */
  @SneakyThrows(JsonProcessingException.class)
  private String jsonString(String s) {
    return mapper.writeValueAsString(s);
  }
}

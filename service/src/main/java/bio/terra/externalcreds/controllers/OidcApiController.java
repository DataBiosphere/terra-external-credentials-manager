package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.BadRequestException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.generated.model.PassportProvider;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.JwtUtils;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.PassportProviderService;
import bio.terra.externalcreds.services.PassportService;
import bio.terra.externalcreds.services.ProviderService;
import bio.terra.externalcreds.services.TokenProviderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record OidcApiController(
    AuditLogger auditLogger,
    HttpServletRequest request,
    JwtUtils jwtUtils,
    LinkedAccountService linkedAccountService,
    ObjectMapper mapper,
    PassportService passportService,
    ProviderService providerService,
    TokenProviderService tokenProviderService,
    PassportProviderService passportProviderService,
    ExternalCredsSamUserFactory samUserFactory)
    implements OidcApi {

  @Override
  public ResponseEntity<List<String>> listProviders() {
    var providerNames = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providerNames);

    return ResponseEntity.ok(providerNames);
  }

  @Override
  public ResponseEntity<LinkInfo> getLink(Provider providerName) {
    var samUser = samUserFactory.from(request);
    var linkedAccount =
        linkedAccountService.getLinkedAccount(samUser.getSubjectId(), providerName.toString());
    return ResponseEntity.of(linkedAccount.map(OpenApiConverters.Output::convert));
  }

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

  @Override
  public ResponseEntity<Void> deleteLink(Provider providerName) {
    var samUser = samUserFactory.from(request);
    var deletedLink = providerService.deleteLink(samUser.getSubjectId(), providerName.toString());

    auditLogger.logEvent(
        new AuditLogEvent.Builder()
            .auditLogEventType(AuditLogEventType.LinkDeleted)
            .providerName(providerName.toString())
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr())
            .externalUserId(deletedLink.getExternalUserId())
            .build());

    return ResponseEntity.ok().build();
  }

  public ResponseEntity<String> getProviderAccessToken(Provider providerName) {
    var samUser = samUserFactory.from(request);

    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .providerName(providerName.toString())
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr());

    var accessToken =
        tokenProviderService.getProviderAccessToken(
            samUser.getSubjectId(), providerName, auditLogEventBuilder);
    return ResponseEntity.of(accessToken);
  }

  @Override
  public ResponseEntity<String> getProviderPassport(PassportProvider providerName) {
    var samUser = samUserFactory.from(request);
    var maybeLinkedAccount =
        linkedAccountService.getLinkedAccount(samUser.getSubjectId(), providerName.toString());
    var maybePassport =
        passportService.getPassport(samUser.getSubjectId(), providerName.toString());

    auditLogger.logEvent(
        new AuditLogEvent.Builder()
            .auditLogEventType(AuditLogEventType.GetPassport)
            .providerName(providerName.toString())
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr())
            .externalUserId(maybeLinkedAccount.map(LinkedAccount::getExternalUserId))
            .build());

    var response =
        maybePassport.flatMap(
            passport -> {
              // passport should not be expired but if it is (due to some failure in ECM)
              // don't pass that failure on to the caller
              if (passport.getExpires().before(new Timestamp(System.currentTimeMillis()))) {
                return Optional.empty();
              } else {
                return Optional.of(passport.getJwt());
              }
            });
    return ResponseEntity.of(response);
  }

  /** Helper method to format a string as json. Otherwise it isn't quoted or escaped correctly. */
  @SneakyThrows(JsonProcessingException.class)
  private String jsonString(String s) {
    return mapper.writeValueAsString(s);
  }
}

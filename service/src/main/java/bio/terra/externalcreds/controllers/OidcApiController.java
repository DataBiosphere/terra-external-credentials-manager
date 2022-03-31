package bio.terra.externalcreds.controllers;

import static bio.terra.externalcreds.controllers.UserStatusInfoUtils.getUserIdFromSam;

import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.PassportService;
import bio.terra.externalcreds.services.ProviderService;
import bio.terra.externalcreds.services.SamService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record OidcApiController(
    AuditLogger auditLogger,
    HttpServletRequest request,
    LinkedAccountService linkedAccountService,
    ObjectMapper mapper,
    PassportService passportService,
    ProviderService providerService,
    SamService samService)
    implements OidcApi {

  @Override
  public ResponseEntity<List<String>> listProviders() {
    var providerNames = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providerNames);

    return ResponseEntity.ok(providerNames);
  }

  @Override
  public ResponseEntity<LinkInfo> getLink(String providerName) {
    var userId = getUserIdFromSam(request, samService);
    var linkedAccount = linkedAccountService.getLinkedAccount(userId, providerName);
    return ResponseEntity.of(linkedAccount.map(OpenApiConverters.Output::convert));
  }

  @Override
  public ResponseEntity<String> getAuthUrl(
      String providerName, List<String> scopes, String redirectUri) {
    var userId = getUserIdFromSam(request, samService);

    var authorizationUrl =
        providerService.getProviderAuthorizationUrl(
            userId, providerName, redirectUri, Set.copyOf(scopes));

    return ResponseEntity.of(authorizationUrl.map(this::jsonString));
  }

  @Override
  public ResponseEntity<LinkInfo> createLink(
      String providerName,
      List<String> scopes,
      String redirectUri,
      String state,
      String oauthcode) {
    var userId = getUserIdFromSam(request, samService);

    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .providerName(providerName)
            .userId(userId)
            .clientIP(request.getRemoteAddr());

    try {
      var linkedAccountWithPassportAndVisas =
          providerService.createLink(
              providerName, userId, oauthcode, redirectUri, Set.copyOf(scopes), state);

      auditLogger.logEvent(
          auditLogEventBuilder
              .auditLogEventType(
                  linkedAccountWithPassportAndVisas
                      .map(x -> AuditLogEventType.LinkCreated)
                      .orElse(AuditLogEventType.LinkCreationFailed))
              .build());

      return ResponseEntity.of(
          linkedAccountWithPassportAndVisas.map(
              x -> OpenApiConverters.Output.convert(x.getLinkedAccount())));
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLogEventBuilder.auditLogEventType(AuditLogEventType.LinkCreationFailed).build());
      throw e;
    }
  }

  @Override
  public ResponseEntity<Void> deleteLink(String providerName) {
    String userId = getUserIdFromSam(request, samService);
    providerService.deleteLink(userId, providerName);

    auditLogger.logEvent(
        new AuditLogEvent.Builder()
            .auditLogEventType(AuditLogEventType.LinkDeleted)
            .providerName(providerName)
            .userId(userId)
            .clientIP(request.getRemoteAddr())
            .build());

    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<String> getProviderPassport(String providerName) {
    var userId = getUserIdFromSam(request, samService);
    var maybePassport = passportService.getPassport(userId, providerName);

    auditLogger.logEvent(
        new AuditLogEvent.Builder()
            .auditLogEventType(AuditLogEventType.GetPassport)
            .providerName(providerName)
            .userId(userId)
            .clientIP(request.getRemoteAddr())
            .build());

    var response =
        maybePassport.flatMap(
            passport -> {
              // passport should not be expired but if it is (due to some failure in ECM)
              // don't pass that failure on to the caller
              if (passport.getExpires().before(new Timestamp(System.currentTimeMillis()))) {
                return Optional.empty();
              } else {
                return Optional.of(jsonString(passport.getJwt()));
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

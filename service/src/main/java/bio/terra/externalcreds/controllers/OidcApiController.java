package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.UnauthorizedException;
import bio.terra.common.iam.BearerTokenParser;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.api.OidcApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.services.LinkedAccountService;
import bio.terra.externalcreds.services.PassportService;
import bio.terra.externalcreds.services.ProviderService;
import bio.terra.externalcreds.services.SamService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class OidcApiController implements OidcApi {

  private final HttpServletRequest request;
  private final LinkedAccountService linkedAccountService;
  private final ObjectMapper mapper;
  private final ProviderService providerService;
  private final SamService samService;
  private final PassportService passportService;
  private final AuditLogger auditLogger;

  public OidcApiController(
      HttpServletRequest request,
      LinkedAccountService linkedAccountService,
      ObjectMapper mapper,
      PassportService passportService,
      ProviderService providerService,
      SamService samService,
      AuditLogger auditLogger) {
    this.request = request;
    this.linkedAccountService = linkedAccountService;
    this.mapper = mapper;
    this.passportService = passportService;
    this.providerService = providerService;
    this.samService = samService;
    this.auditLogger = auditLogger;
  }

  private String getUserIdFromSam() {
    try {
      var header = request.getHeader("authorization");
      if (header == null) throw new UnauthorizedException("User is not authorized");
      var accessToken = BearerTokenParser.parse(header);

      return samService.samUsersApi(accessToken).getUserStatusInfo().getUserSubjectId();
    } catch (ApiException e) {
      throw new ExternalCredsException(
          e,
          e.getCode() == HttpStatus.NOT_FOUND.value()
              ? HttpStatus.FORBIDDEN
              : HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @VisibleForTesting
  LinkInfo getLinkInfoFromLinkedAccount(LinkedAccount linkedAccount) {
    return new LinkInfo()
        .externalUserId(linkedAccount.getExternalUserId())
        .expirationTimestamp(linkedAccount.getExpires());
  }

  @Override
  public ResponseEntity<List<String>> listProviders() {
    var providerNames = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providerNames);

    return ResponseEntity.ok(providerNames);
  }

  @Override
  public ResponseEntity<LinkInfo> getLink(String providerName) {
    var userId = getUserIdFromSam();
    var linkedAccount = linkedAccountService.getLinkedAccount(userId, providerName);
    return ResponseEntity.of(linkedAccount.map(this::getLinkInfoFromLinkedAccount));
  }

  @Override
  public ResponseEntity<String> getAuthUrl(
      String providerName, List<String> scopes, String redirectUri, String state) {
    var authorizationUrl =
        providerService.getProviderAuthorizationUrl(
            providerName, redirectUri, Set.copyOf(scopes), state);

    return ResponseEntity.of(authorizationUrl.map(this::jsonString));
  }

  @Override
  public ResponseEntity<LinkInfo> createLink(
      String providerName,
      List<String> scopes,
      String redirectUri,
      String state,
      String oauthcode) {
    var userId = getUserIdFromSam();

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
              x -> getLinkInfoFromLinkedAccount(x.getLinkedAccount())));
    } catch (Exception e) {
      auditLogger.logEvent(
          auditLogEventBuilder.auditLogEventType(AuditLogEventType.LinkCreationFailed).build());
      throw e;
    }
  }

  @Override
  public ResponseEntity<Void> deleteLink(String providerName) {
    String userId = getUserIdFromSam();
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
    var userId = getUserIdFromSam();
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

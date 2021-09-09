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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    var expTime =
        OffsetDateTime.ofInstant(linkedAccount.getExpires().toInstant(), ZoneId.of("UTC"));
    return new LinkInfo()
        .externalUserId(linkedAccount.getExternalUserId())
        .expirationTimestamp(expTime);
  }

  @Override
  public ResponseEntity<List<String>> listProviders() {
    var providers = new ArrayList<>(providerService.getProviderList());
    Collections.sort(providers);

    return ResponseEntity.ok(providers);
  }

  @Override
  public ResponseEntity<LinkInfo> getLink(String provider) {
    var userId = getUserIdFromSam();
    var linkedAccount = linkedAccountService.getLinkedAccount(userId, provider);
    return ResponseEntity.of(linkedAccount.map(this::getLinkInfoFromLinkedAccount));
  }

  @Override
  public ResponseEntity<String> getAuthUrl(
      String provider, List<String> scopes, String redirectUri, String state) {
    var authorizationUrl =
        providerService.getProviderAuthorizationUrl(
            provider, redirectUri, Set.copyOf(scopes), state);

    return ResponseEntity.of(authorizationUrl.map(this::jsonString));
  }

  @Override
  public ResponseEntity<LinkInfo> createLink(
      String provider, List<String> scopes, String redirectUri, String state, String oauthcode) {
    var userId = getUserIdFromSam();

    try {
      var linkedAccountWithPassportAndVisas =
          providerService.createLink(
              provider, userId, oauthcode, redirectUri, Set.copyOf(scopes), state);

      auditLogger.logEvent(
          new AuditLogEvent.Builder()
              .eventType(AuditLogEventType.LinkCreated)
              .provider(provider)
              .userId(userId)
              .clientIP(request.getRemoteAddr())
              .build());

      return ResponseEntity.of(
          linkedAccountWithPassportAndVisas.map(
              x -> getLinkInfoFromLinkedAccount(x.getLinkedAccount())));
    } catch (Exception e) {
      auditLogger.logEvent(
          new AuditLogEvent.Builder()
              .eventType(AuditLogEventType.LinkCreationFailed)
              .provider(provider)
              .userId(userId)
              .clientIP(request.getRemoteAddr())
              .build());
      throw e;
    }
  }

  @Override
  public ResponseEntity<Void> deleteLink(String provider) {
    String userId = getUserIdFromSam();
    providerService.deleteLink(userId, provider);

    auditLogger.logEvent(
        new AuditLogEvent.Builder()
            .eventType(AuditLogEventType.LinkDeleted)
            .provider(provider)
            .userId(userId)
            .clientIP(request.getRemoteAddr())
            .build());

    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<String> getProviderPassport(String provider) {
    var userId = getUserIdFromSam();
    var passport = passportService.getPassport(userId, provider);

    auditLogger.logEvent(
        new AuditLogEvent.Builder()
            .eventType(AuditLogEventType.GetPassport)
            .provider(provider)
            .userId(userId)
            .clientIP(request.getRemoteAddr())
            .build());

    return ResponseEntity.of(passport.map(p -> jsonString(p.getJwt())));
  }

  /** Helper method to format a string as json. Otherwise it isn't quoted or escaped correctly. */
  @SneakyThrows(JsonProcessingException.class)
  private String jsonString(String s) {
    return mapper.writeValueAsString(s);
  }
}

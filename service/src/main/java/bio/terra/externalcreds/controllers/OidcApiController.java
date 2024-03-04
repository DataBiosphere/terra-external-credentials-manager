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
  public ResponseEntity<LinkInfo> getLink(Provider provider) {
    var samUser = samUserFactory.from(request);
    var linkedAccount = linkedAccountService.getLinkedAccount(samUser.getSubjectId(), provider);
    return ResponseEntity.of(linkedAccount.map(OpenApiConverters.Output::convert));
  }

  @Override
  public ResponseEntity<String> getAuthUrl(Provider provider, String redirectUri) {
    var samUser = samUserFactory.from(request);

    var authorizationUrl =
        providerService.getProviderAuthorizationUrl(samUser.getSubjectId(), provider, redirectUri);

    return ResponseEntity.of(authorizationUrl.map(this::jsonString));
  }

  @Override
  public ResponseEntity<LinkInfo> createLink(Provider provider, String state, String oauthcode) {
    var samUser = samUserFactory.from(request);

    var auditLogEventBuilder =
        new AuditLogEvent.Builder()
            .provider(provider)
            .userId(samUser.getSubjectId())
            .clientIP(request.getRemoteAddr());

    try {
      if (provider.equals(Provider.RAS)) {
        var linkedAccountWithPassportAndVisas =
            passportProviderService.createLink(
                provider, samUser.getSubjectId(), oauthcode, state, auditLogEventBuilder);
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

  @Override
  public ResponseEntity<String> getProviderPassport(PassportProvider passportProvider) {
    var samUser = samUserFactory.from(request);
    var provider = Provider.fromValue(passportProvider.toString());
    var maybeLinkedAccount =
        linkedAccountService.getLinkedAccount(samUser.getSubjectId(), provider);
    var maybePassport = passportService.getPassport(samUser.getSubjectId(), provider);

    auditLogger.logEvent(
        new AuditLogEvent.Builder()
            .auditLogEventType(AuditLogEventType.GetPassport)
            .provider(provider)
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

package bio.terra.externalcreds.controllers;

import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.generated.api.OauthApi;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.services.*;
import bio.terra.externalcreds.util.ProviderUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public record OauthApiController(
    AuditLogger auditLogger,
    HttpServletRequest request,
    ObjectMapper mapper,
    LinkedAccountService linkedAccountService,
    ProviderService providerService,
    PassportProviderService passportProviderService,
    TokenProviderService tokenProviderService,
    FenceProviderService fenceProviderService,
    ExternalCredsSamUserFactory samUserFactory)
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
    var linkedAccount =
        switch (provider) {
          case RAS, GITHUB -> linkedAccountService.getLinkedAccount(
              samUser.getSubjectId(), provider);
          case FENCE, DCF_FENCE, KIDS_FIRST, ANVIL -> fenceProviderService.getLinkedFenceAccount(
              samUser.getSubjectId(), provider);
        };
    return ResponseEntity.of(linkedAccount.map(OpenApiConverters.Output::convert));
  }

  @Override
  public ResponseEntity<String> getAuthorizationUrl(Provider provider, String redirectUri) {
    var samUser = samUserFactory.from(request);

    var authorizationUrl =
        providerService.getProviderAuthorizationUrl(samUser.getSubjectId(), provider, redirectUri);

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
        tokenProviderService.getProviderAccessToken(
            samUser.getSubjectId(), provider, auditLogEventBuilder);
    return ResponseEntity.of(accessToken);
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
      LinkInfo linkInfo =
          switch (provider) {
            case RAS -> {
              var linkedAccountWithPassportAndVisas =
                  passportProviderService.createLink(
                      provider, samUser.getSubjectId(), oauthcode, state, auditLogEventBuilder);
              yield OpenApiConverters.Output.convert(
                  linkedAccountWithPassportAndVisas.getLinkedAccount());
            }
            case GITHUB -> {
              var linkedAccount =
                  tokenProviderService.createLink(
                      provider, samUser.getSubjectId(), oauthcode, state, auditLogEventBuilder);
              yield OpenApiConverters.Output.convert(linkedAccount);
            }
            case FENCE, DCF_FENCE, KIDS_FIRST, ANVIL -> {
              var linkedAccount =
                  fenceProviderService.createLink(
                      provider, samUser.getSubjectId(), oauthcode, state, auditLogEventBuilder);
              yield OpenApiConverters.Output.convert(linkedAccount);
            }
          };
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

    // This can be deleted once there's no longer a dependency on Bond's Datastore
    if (ProviderUtils.isFenceProvider(provider)) {
      fenceProviderService.deleteBondFenceLink(samUser.getSubjectId(), provider);
    }

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

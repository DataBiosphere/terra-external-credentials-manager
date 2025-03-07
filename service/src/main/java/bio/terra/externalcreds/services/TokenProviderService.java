package bio.terra.externalcreds.services;

import bio.terra.common.exception.ForbiddenException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.generated.model.Provider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TokenProviderService extends ProviderService {

  private final AccessTokenCacheService accessTokenCacheService;

  public TokenProviderService(
      ExternalCredsConfig externalCredsConfig,
      ProviderOAuthClientCache providerOAuthClientCache,
      ProviderTokenClientCache providerTokenClientCache,
      OAuth2Service oAuth2Service,
      LinkedAccountService linkedAccountService,
      AuditLogger auditLogger,
      ObjectMapper objectMapper,
      AccessTokenCacheService accessTokenCacheService) {
    super(
        externalCredsConfig,
        providerOAuthClientCache,
        providerTokenClientCache,
        oAuth2Service,
        linkedAccountService,
        auditLogger,
        objectMapper);
    this.accessTokenCacheService = accessTokenCacheService;
  }

  public String getProviderAccessToken(
      String userId, Provider provider, AuditLogEvent.Builder auditLogEventBuilder) {
    var linkedAccount =
        linkedAccountService
            .getLinkedAccount(userId, provider)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        String.format(
                            "No linked account found for user ID: %s and provider: %s. "
                                + "Please go to the Terra Profile page External Identities tab "
                                + "to link your account for this provider.",
                            userId, provider)));
    if (linkedAccount.getExpires().before(Timestamp.from(Instant.now()))) {
      throw new ForbiddenException(
          String.format(
              "The linked account found for user ID: %s and provider: %s has expired. "
                  + "Please go to the Terra Profile page External Identities tab "
                  + "to re-link your account for this provider.",
              userId, provider));
    }
    var providerProperties = externalCredsConfig.getProviderProperties(provider);
    return accessTokenCacheService.getLinkedAccountAccessToken(
        linkedAccount, new HashSet<>(providerProperties.getScopes()), auditLogEventBuilder);
  }
}

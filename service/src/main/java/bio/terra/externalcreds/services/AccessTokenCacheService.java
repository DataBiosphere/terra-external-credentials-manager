package bio.terra.externalcreds.services;

import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.auditLogging.AuditLogEvent;
import bio.terra.externalcreds.auditLogging.AuditLogEventType;
import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.AccessTokenCacheDAO;
import bio.terra.externalcreds.models.AccessTokenCacheEntry;
import bio.terra.externalcreds.models.LinkedAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AccessTokenCacheService {

  private final ProviderTokenClientCache providerTokenClientCache;
  private final LinkedAccountService linkedAccountService;
  private final OAuth2Service oAuth2Service;
  private final AccessTokenCacheDAO accessTokenCacheDAO;
  private final ExternalCredsConfig externalCredsConfig;
  private final AuditLogger auditLogger;

  public AccessTokenCacheService(
      ProviderTokenClientCache providerTokenClientCache,
      LinkedAccountService linkedAccountService,
      OAuth2Service oAuth2Service,
      AccessTokenCacheDAO accessTokenCacheDAO,
      ExternalCredsConfig externalCredsConfig,
      AuditLogger auditLogger) {
    this.providerTokenClientCache = providerTokenClientCache;
    this.linkedAccountService = linkedAccountService;
    this.oAuth2Service = oAuth2Service;
    this.accessTokenCacheDAO = accessTokenCacheDAO;
    this.externalCredsConfig = externalCredsConfig;
    this.auditLogger = auditLogger;
  }

  @WriteTransaction
  public String getLinkedAccountAccessToken(
      LinkedAccount linkedAccount, Set<String> scopes, AuditLogEvent.Builder auditLogEventBuilder) {
    var tokenCacheEntry =
        getAccessTokenCacheEntry(linkedAccount)
            .flatMap(
                tokenEntry -> {
                  if (tokenEntry
                      .getExpiresAt()
                      .isAfter(
                          Instant.now()
                              .plus(externalCredsConfig.getAccessTokenExpirationBuffer()))) {
                    return Optional.of(tokenEntry.getAccessToken());
                  }
                  return Optional.empty();
                });

    return tokenCacheEntry.orElseGet(
        () -> getNewProviderAccessToken(linkedAccount, scopes, auditLogEventBuilder));
  }

  private String getNewProviderAccessToken(
      LinkedAccount linkedAccount, Set<String> scopes, AuditLogEvent.Builder auditLogEventBuilder) {
    // get client registration from provider client cache
    var clientRegistration =
        providerTokenClientCache.getProviderClient(linkedAccount.getProvider());

    // exchange refresh token for access token
    var accessTokenResponse =
        oAuth2Service.authorizeWithRefreshToken(
            clientRegistration,
            new OAuth2RefreshToken(linkedAccount.getRefreshToken(), null),
            scopes);

    // save the linked account with the new refresh token to replace the old one
    var refreshToken = accessTokenResponse.getRefreshToken();
    if (refreshToken != null) {
      linkedAccountService.upsertLinkedAccount(
          linkedAccount.withRefreshToken(refreshToken.getTokenValue()));
    }
    logGetProviderAccessToken(linkedAccount, auditLogEventBuilder);

    return upsertAccessTokenCacheEntry(
            new AccessTokenCacheEntry.Builder()
                .linkedAccountId(linkedAccount.getId().orElseThrow())
                .accessToken(accessTokenResponse.getAccessToken().getTokenValue())
                .expiresAt(accessTokenResponse.getAccessToken().getExpiresAt())
                .build())
        .getAccessToken();
  }

  private Optional<AccessTokenCacheEntry> getAccessTokenCacheEntry(LinkedAccount linkedAccount) {
    return accessTokenCacheDAO.getAccessTokenCacheEntry(linkedAccount);
  }

  private AccessTokenCacheEntry upsertAccessTokenCacheEntry(
      AccessTokenCacheEntry accessTokenCacheEntry) {
    return accessTokenCacheDAO.upsertAccessTokenCacheEntry(accessTokenCacheEntry);
  }

  public boolean deleteAccessTokenCacheEntry(int linkedAccountId) {
    return accessTokenCacheDAO.deleteAccessTokenCacheEntry(linkedAccountId);

  }

  public void logGetProviderAccessToken(
      LinkedAccount linkedAccount, AuditLogEvent.Builder auditLogEventBuilder) {
    auditLogger.logEvent(
        auditLogEventBuilder
            .externalUserId(linkedAccount.getExternalUserId())
            .userId(linkedAccount.getUserId())
            .provider(linkedAccount.getProvider())
            .auditLogEventType(AuditLogEventType.GetProviderAccessToken)
            .build());
  }
}

package bio.terra.externalcreds.services;

import bio.terra.externalcreds.auditLogging.AuditLogger;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.BondDatastoreDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.BondFenceServiceAccountEntity;
import bio.terra.externalcreds.models.BondRefreshTokenEntity;
import bio.terra.externalcreds.models.LinkedAccount;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FenceProviderService extends ProviderService {

  private final BondDatastoreDAO bondDatastoreDAO;
  public FenceProviderService(
      ExternalCredsConfig externalCredsConfig,
      ProviderOAuthClientCache providerOAuthClientCache,
      ProviderTokenClientCache providerTokenClientCache,
      OAuth2Service oAuth2Service,
      LinkedAccountService linkedAccountService,
      BondDatastoreDAO bondDatastoreDAO,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    super(
        externalCredsConfig,
        providerOAuthClientCache,
        providerTokenClientCache,
        oAuth2Service,
        linkedAccountService,
        auditLogger,
        objectMapper);
    this.bondDatastoreDAO = bondDatastoreDAO;
  }

  public Optional<LinkedAccount> getBondLinkedAccount(String userId, Provider provider) {
    var bondRefreshTokenEntity = bondDatastoreDAO.getRefreshToken(userId, provider);
    var providerProperties = externalCredsConfig.getProviders().get(provider.toString());
    var bondLinkedAccount = bondRefreshTokenEntity.map(refreshTokenEntity -> new LinkedAccount.Builder()
        .providerName(provider.toString())
        .userId(userId)
        .expires(new Timestamp(refreshTokenEntity.getIssuedAt().plus(providerProperties.getLinkLifespan().toDays(), ChronoUnit.DAYS).toEpochMilli()))
        .externalUserId(refreshTokenEntity.getUsername())
        .refreshToken(refreshTokenEntity.getToken())
        .isAuthenticated(true)
        .build());
    return bondLinkedAccount.map(linkedAccountService::upsertLinkedAccount);
  }

  public Optional<BondFenceServiceAccountEntity> getBondFenceServiceAccountKey(String userId, Provider provider) {
    return bondDatastoreDAO.getFenceServiceAccountKey(userId, provider);
  }

  public void deleteBondLinkedAccount(String userId, Provider provider) {
    // TODO: We also need to revoke the refresh token
    bondDatastoreDAO.deleteRefreshToken(userId, provider);
    bondDatastoreDAO.deleteFenceServiceAccountKey(userId, provider);
  }

}

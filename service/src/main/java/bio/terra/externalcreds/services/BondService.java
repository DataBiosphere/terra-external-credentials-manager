package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.BondDatastoreDAO;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BondService {

  private final ExternalCredsConfig externalCredsConfig;
  private final BondDatastoreDAO bondDatastoreDAO;

  public BondService(ExternalCredsConfig externalCredsConfig, BondDatastoreDAO bondDatastoreDAO) {
    this.externalCredsConfig = externalCredsConfig;
    this.bondDatastoreDAO = bondDatastoreDAO;
  }

  public Optional<LinkedAccount> getLinkedAccount(String userId, Provider provider) {
    var bondRefreshTokenEntity = bondDatastoreDAO.getRefreshToken(userId, provider);
    var providerProperties = externalCredsConfig.getProviderProperties(provider);
    return bondRefreshTokenEntity.map(
        refreshTokenEntity ->
            new LinkedAccount.Builder()
                .provider(provider)
                .userId(userId)
                .expires(
                    new Timestamp(
                        refreshTokenEntity
                            .getIssuedAt()
                            .plus(providerProperties.getLinkLifespan().toDays(), ChronoUnit.DAYS)
                            .toEpochMilli()))
                .externalUserId(refreshTokenEntity.getUsername())
                .refreshToken(refreshTokenEntity.getToken())
                .isAuthenticated(true)
                .build());
  }

  public Optional<FenceAccountKey> getFenceServiceAccountKey(
      String userId, Provider provider, Integer linkedAccountId) {
    return bondDatastoreDAO
        .getFenceServiceAccountKey(userId, provider)
        .map(
            fenceServiceAccountKeyEntity ->
                new FenceAccountKey.Builder()
                    .keyJson(fenceServiceAccountKeyEntity.getKeyJson())
                    .expiresAt(fenceServiceAccountKeyEntity.getExpiresAt())
                    .linkedAccountId(linkedAccountId)
                    .build());
  }

  public void deleteBondLinkedAccount(String userId, Provider provider) {
    bondDatastoreDAO.deleteRefreshToken(userId, provider);
    bondDatastoreDAO.deleteFenceServiceAccountKey(userId, provider);
  }
}

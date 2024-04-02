package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.dataAccess.AccessTokenCacheDAO;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.AccessTokenCacheEntry;
import bio.terra.externalcreds.models.LinkedAccount;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AccessTokenCacheService {
  private final AccessTokenCacheDAO accessTokenCacheDAO;

  public AccessTokenCacheService(AccessTokenCacheDAO accessTokenCacheDAO) {
    this.accessTokenCacheDAO = accessTokenCacheDAO;
  }

  @ReadTransaction
  public Optional<AccessTokenCacheEntry> getAccessTokenCacheEntry(
      String userId, Provider provider) {
    return accessTokenCacheDAO.getAccessTokenCacheEntry(userId, provider);
  }

  @ReadTransaction
  public Optional<AccessTokenCacheEntry> getAccessTokenCacheEntry(LinkedAccount linkedAccount) {
    return accessTokenCacheDAO.getAccessTokenCacheEntry(linkedAccount);
  }

  @WriteTransaction
  public AccessTokenCacheEntry upsertAccessTokenCacheEntry(
      AccessTokenCacheEntry accessTokenCacheEntry) {
    return accessTokenCacheDAO.upsertAccessTokenCacheEntry(accessTokenCacheEntry);
  }
}

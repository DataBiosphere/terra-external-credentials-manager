package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.dataAccess.FenceAccountKeyDAO;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.LinkedAccount;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FenceAccountKeyService {

  private final FenceAccountKeyDAO fenceAccountKeyDAO;

  public FenceAccountKeyService(FenceAccountKeyDAO fenceAccountKeyDAO) {
    this.fenceAccountKeyDAO = fenceAccountKeyDAO;
  }

  @WriteTransaction
  public FenceAccountKey upsertFenceAccountKey(FenceAccountKey fenceAccountKey) {
    return fenceAccountKeyDAO.upsertFenceAccountKey(fenceAccountKey);
  }

  @ReadTransaction
  public Optional<FenceAccountKey> getFenceAccountKey(LinkedAccount linkedAccount) {
    return fenceAccountKeyDAO.getFenceAccountKey(linkedAccount);
  }
}

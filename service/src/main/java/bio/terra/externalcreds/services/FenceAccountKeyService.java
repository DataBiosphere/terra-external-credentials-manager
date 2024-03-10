package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.externalcreds.dataAccess.FenceAccountKeyDAO;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.*;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FenceAccountKeyService {

  private final FenceAccountKeyDAO fenceAccountKeyDAO;

  public FenceAccountKeyService(FenceAccountKeyDAO fenceAccountKeyDAO) {
    this.fenceAccountKeyDAO = fenceAccountKeyDAO;
  }

  @ReadTransaction
  public Optional<FenceAccountKey> getFenceAccountKey(String userId, Provider provider) {
    return fenceAccountKeyDAO.getFenceAccountKey(userId, provider);
  }
}

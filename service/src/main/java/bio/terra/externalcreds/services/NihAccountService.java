package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.dataAccess.NihAccountDAO;
import bio.terra.externalcreds.models.NihAccount;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NihAccountService {

  private final NihAccountDAO nihAccountDAO;

  public NihAccountService(NihAccountDAO nihAccountDAO) {
    this.nihAccountDAO = nihAccountDAO;
  }

  @ReadTransaction
  public Optional<NihAccount> getNihAccountForUser(String userId) {
    return nihAccountDAO.getNihAccount(userId);
  }

  @ReadTransaction
  public Optional<NihAccount> getLinkedAccountForUsername(String nihUsername) {
    return nihAccountDAO.getNihAccountForUsername(nihUsername);
  }

  @WriteTransaction
  public NihAccount upsertNihAccount(NihAccount nihAccount) {
    return nihAccountDAO.upsertNihAccount(nihAccount);
  }

  @WriteTransaction
  public boolean deleteNihAccount(String userId) {
    return nihAccountDAO.deleteNihAccountIfExists(userId);
  }

  @ReadTransaction
  public List<NihAccount> getExpiredNihAccounts() {
    return nihAccountDAO.getExpiredNihAccounts();
  }

  @ReadTransaction
  public List<NihAccount> getActiveNihAccounts() {
    return nihAccountDAO.getActiveNihAccounts();
  }
}

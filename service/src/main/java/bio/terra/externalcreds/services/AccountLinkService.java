package bio.terra.externalcreds.services;

import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AccountLinkService {

  private final LinkedAccountDAO linkedAccountDAO;

  public AccountLinkService(LinkedAccountDAO linkedAccountDAO) {
    this.linkedAccountDAO = linkedAccountDAO;
  }

  public LinkInfo getAccountLink(String userId, String providerId) {
    LinkedAccount link = linkedAccountDAO.getLinkedAccount(userId, providerId);
    OffsetDateTime expTime =
        OffsetDateTime.ofInstant(
            link.getExpires().toInstant(), ZoneId.of("UTC")); // TODO unit test this conversion
    return new LinkInfo().externalUserId(link.getExternalUserId()).expirationTimestamp(expTime);
  }
}

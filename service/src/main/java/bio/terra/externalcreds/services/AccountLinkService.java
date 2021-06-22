package bio.terra.externalcreds.services;

import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.generated.model.LinkInfo;
import bio.terra.externalcreds.models.LinkedAccount;
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
    LinkInfo linkInfo = new LinkInfo();
    try {
      LinkedAccount link = linkedAccountDAO.getLinkedAccount(userId, providerId);
      linkInfo.setExternalUserId(link.getExternalUserId());
      OffsetDateTime expTime =
          OffsetDateTime.ofInstant(
              link.getExpires().toInstant(),
              ZoneId.of("UTC")); // TODO validate this conversion in some way
      linkInfo.setExpirationTimestamp(expTime);
      return linkInfo;
    } catch (Exception e) {
      log.warn("Error getting linked account information", e);
      // TODO return something here?
      linkInfo.setExternalUserId(e.getMessage());
      return linkInfo;
    }
  }
}

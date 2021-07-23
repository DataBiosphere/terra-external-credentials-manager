package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.common.exception.NotFoundException;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class LinkedAccountService {

  private final LinkedAccountDAO linkedAccountDAO;
  private final GA4GHPassportDAO ga4ghPassportDAO;
  private final GA4GHVisaDAO ga4ghVisaDAO;
  private final ProviderConfig providerConfig;

  public LinkedAccountService(
      LinkedAccountDAO linkedAccountDAO,
      GA4GHPassportDAO ga4ghPassportDAO,
      GA4GHVisaDAO ga4ghVisaDAO,
      ProviderConfig providerConfig) {
    this.linkedAccountDAO = linkedAccountDAO;
    this.ga4ghPassportDAO = ga4ghPassportDAO;
    this.ga4ghVisaDAO = ga4ghVisaDAO;
    this.providerConfig = providerConfig;
  }

  @ReadTransaction
  public LinkedAccount getLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.getLinkedAccount(userId, providerId);
  }

  @WriteTransaction
  public LinkedAccount saveLinkedAccount(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas) {
    var savedLinkedAccount =
        linkedAccountDAO.upsertLinkedAccount(linkedAccountWithPassportAndVisas.getLinkedAccount());

    // clear out any passport and visas that may exist and save the new one
    ga4ghPassportDAO.deletePassport(savedLinkedAccount.getId());
    savePassportIfExists(linkedAccountWithPassportAndVisas, savedLinkedAccount.getId());

    return savedLinkedAccount;
  }

  @WriteTransaction
  public boolean deleteLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.deleteLinkedAccountIfExists(userId, providerId);
  }

  private void savePassportIfExists(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas, int linkedAccountId) {
    if (linkedAccountWithPassportAndVisas.getPassport() != null) {

      var savedPassport =
          ga4ghPassportDAO.insertPassport(
              linkedAccountWithPassportAndVisas.getPassport().withLinkedAccountId(linkedAccountId));

      linkedAccountWithPassportAndVisas
          .getVisas()
          .forEach(v -> ga4ghVisaDAO.insertVisa(v.withPassportId(savedPassport.getId())));
    }
  }

  @WriteTransaction
  public void deleteLinkedAccountAndRevokeToken(String userId, String providerId) {
    LinkedAccount linkedAccount = linkedAccountDAO.getLinkedAccount(userId, providerId);

    if (linkedAccount == null) {
      throw new NotFoundException("Linked account not found.");
    } else {
      revokeRefreshToken(providerId, linkedAccount.getRefreshToken());
      var deleteSucceeded = linkedAccountDAO.deleteLinkedAccountIfExists(userId, providerId);
      if (!deleteSucceeded)
        // The transaction should prevent this from ever happening, but we check anyway
        throw new ExternalCredsException(
            String.format(
                "Refresh token was revoked but linked account not deletion failed: userId=%s and providerId=%s",
                userId, providerId));
    }
  }

  @VisibleForTesting
  public void revokeRefreshToken(String providerId, String refreshToken) {
    ProviderConfig.ProviderInfo providerInfo = providerConfig.getServices().get(providerId);

    if (providerInfo == null)
      throw new NotFoundException(String.format("Provider %s not found", providerId));

    // Get the endpoint URL and insert the token
    String revokeEndpoint = String.format(providerInfo.getRevokeEndpoint(), refreshToken);
    // Add authorization information and make request
    WebClient.ResponseSpec response = // TODO mock this endpoint in the tests
        WebClient.create(revokeEndpoint)
            .post()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .queryParam("client_id", providerInfo.getClientId())
                        .queryParam("client_secret", providerInfo.getClientSecret())
                        .build())
            .retrieve();
    // handle the response
    String responseBody =
        response
            .onStatus(
                HttpStatus::isError,
                clientResponse ->
                    Mono.error(
                        new ExternalCredsException(
                            "Encountered an error while revoking the refresh token."))) // TODO test
            // that this
            // error is
            // thrown
            .bodyToMono(String.class)
            .block(Duration.of(1000, ChronoUnit.MILLIS));

    // TODO: figure out whether to log the response
    log.info("Token revocation request returned with the result: " + responseBody);
  }
}

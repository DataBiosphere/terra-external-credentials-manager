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
import java.util.stream.Collectors;
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
  public LinkedAccountWithPassportAndVisas saveLinkedAccount(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas) {
    var savedLinkedAccount =
        linkedAccountDAO.upsertLinkedAccount(linkedAccountWithPassportAndVisas.getLinkedAccount());

    // clear out any passport and visas that may exist and save the new one
    ga4ghPassportDAO.deletePassport(savedLinkedAccount.getId());

    return savePassportIfExists(
        linkedAccountWithPassportAndVisas.withLinkedAccount(savedLinkedAccount));
  }

  @WriteTransaction
  public boolean deleteLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.deleteLinkedAccountIfExists(userId, providerId);
  }

  private LinkedAccountWithPassportAndVisas savePassportIfExists(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas) {
    if (linkedAccountWithPassportAndVisas.getPassport() != null) {

      var savedPassport =
          ga4ghPassportDAO.insertPassport(
              linkedAccountWithPassportAndVisas
                  .getPassport()
                  .withLinkedAccountId(
                      linkedAccountWithPassportAndVisas.getLinkedAccount().getId()));

      var savedVisas =
          linkedAccountWithPassportAndVisas.getVisas().stream()
              .map(v -> ga4ghVisaDAO.insertVisa(v.withPassportId(savedPassport.getId())))
              .collect(Collectors.toList());

      return linkedAccountWithPassportAndVisas.withPassport(savedPassport).withVisas(savedVisas);
    } else {
      return linkedAccountWithPassportAndVisas;
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

  private void revokeRefreshToken(String providerId, String refreshToken) {
    ProviderConfig.ProviderInfo providerInfo = providerConfig.getServices().get(providerId);

    if (providerInfo == null)
      throw new NotFoundException(String.format("Provider %s not found", providerId));

    // Get the endpoint URL and insert the token
    String revokeEndpoint = String.format(providerInfo.getRevokeEndpoint(), refreshToken);
    // Add authorization information and make request
    WebClient.ResponseSpec response =
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
                    Mono.error( // TODO: decide whether to log the status code
                        new ExternalCredsException(
                            "Encountered an error while revoking the refresh token.")))
            // that this
            // error is
            // thrown
            .bodyToMono(String.class)
            .block(Duration.of(1000, ChronoUnit.MILLIS));

    log.info("Token revocation request returned with the result: " + responseBody);
  }
}

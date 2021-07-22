package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
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

  public String revokeProviderLink(String userId, String providerId) {
    LinkedAccount linkedAccount = getLinkedAccount(userId, providerId);
    ProviderConfig.ProviderInfo providerInfo = providerConfig.getServices().get(providerId);
    ;
    if (providerInfo == null) throw new ExternalCredsException("Provider not found");

    // TODO:
    //  - use WebTestClient to test
    //  - figure out whether to log the response
    //  - change return type back to void

    // Get the endpoint with the refresh token
    System.out.println("___________" + providerInfo.getRevokeEndpoint());
    String revokeEndpoint =
        String.format(providerInfo.getRevokeEndpoint(), linkedAccount.getRefreshToken());
    // Add authorization information and make request
    WebClient.ResponseSpec response =
        WebClient.create(revokeEndpoint)
            .post()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .queryParam("client_id", "hello") // providerInfo.getClientId())
                        .queryParam("client_secret", "hello") // providerInfo.getClientSecret())
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
                            "ahhh"))) // TODO figure out what exception to throw here
            .bodyToMono(String.class)
            .block(Duration.of(1000, ChronoUnit.MILLIS));
    log.info("Token revocation request returned with message: " + responseBody);
    return responseBody;
  }
}

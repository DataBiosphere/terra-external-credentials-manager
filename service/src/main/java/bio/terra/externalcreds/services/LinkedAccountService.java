package bio.terra.externalcreds.services;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.dataAccess.ReadTransaction;
import bio.terra.externalcreds.models.LinkedAccount;
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
  private final ProviderService providerService;

  public LinkedAccountService(LinkedAccountDAO linkedAccountDAO, ProviderService providerService) {
    this.linkedAccountDAO = linkedAccountDAO;
    this.providerService = providerService;
  }

  @ReadTransaction
  public LinkedAccount getLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.getLinkedAccount(userId, providerId);
  }

  public boolean deleteLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.deleteLinkedAccount(userId, providerId);
  }

  public String revokeProviderLink(String userId, String providerId) {
    LinkedAccount linkedAccount = getLinkedAccount(userId, providerId);
    ProviderConfig.ProviderInfo providerInfo = providerService.getProviderInfo(providerId);
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

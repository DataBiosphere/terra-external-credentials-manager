package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.models.GA4GHPassport;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
public class PassportService {

  private final GA4GHPassportDAO passportDAO;
  private final ExternalCredsConfig externalCredsConfig;

  public PassportService(GA4GHPassportDAO passportDAO, ExternalCredsConfig externalCredsConfig) {
    this.passportDAO = passportDAO;
    this.externalCredsConfig = externalCredsConfig;
  }

  @ReadTransaction
  public Optional<GA4GHPassport> getPassport(String userId, String providerId) {
    return passportDAO.getPassport(userId, providerId);
  }

  @WriteTransaction
  public boolean deletePassport(int linkedAccountId) {
    return passportDAO.deletePassport(linkedAccountId);
  }

  @ReadTransaction
  public void validatePassportsWithTokenVisas() {
    var passportDetailsList = passportDAO.getPassportsWithUnvalidatedTokenVisas();

    passportDetailsList.forEach(
        pd -> {
          var validationEndpoint =
              externalCredsConfig.getProviders().get(pd.getProviderId()).getValidationEndpoint().get();

          var response = WebClient.create(validationEndpoint)
              .post()
              .bodyValue(pd.getPassportJwt())
              .retrieve();

          /*
          // Get the endpoint URL and insert the token
          String revokeEndpoint =
              String.format(providerProperties.getRevokeEndpoint(), linkedAccount.getRefreshToken());
          // Add authorization information and make request
          WebClient.ResponseSpec response =
              WebClient.create(revokeEndpoint)
                  .post()
                  .uri(
                      uriBuilder ->
                          uriBuilder
                              .queryParam("client_id", providerProperties.getClientId())
                              .queryParam("client_secret", providerProperties.getClientSecret())
                              .build())
                  .retrieve();

          String responseBody =
              response
                  .onStatus(HttpStatus::isError, clientResponse -> Mono.empty())
                  .bodyToMono(String.class)
                  .block(Duration.of(1000, ChronoUnit.MILLIS));

          log.info(
              "Token revocation request for user [{}], provider [{}] returned with the result: [{}]",
              linkedAccount.getUserId(),
              linkedAccount.getProviderId(),
              responseBody);

                */

        });
  }
}

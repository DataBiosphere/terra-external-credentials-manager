package bio.terra.externalcreds.services;

import bio.terra.common.db.ReadTransaction;
import bio.terra.common.db.WriteTransaction;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GA4GHPassportDAO;
import bio.terra.externalcreds.dataAccess.GA4GHVisaDAO;
import bio.terra.externalcreds.dataAccess.LinkedAccountDAO;
import bio.terra.externalcreds.models.AuthorizationChangeEvent;
import bio.terra.externalcreds.models.LinkedAccount;
import bio.terra.externalcreds.models.LinkedAccountWithPassportAndVisas;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LinkedAccountService {

  private final LinkedAccountDAO linkedAccountDAO;
  private final GA4GHPassportDAO ga4ghPassportDAO;
  private final GA4GHVisaDAO ga4ghVisaDAO;
  private final ObjectMapper objectMapper;
  private final Optional<Publisher> authorizationChangeEventPublisher;

  public LinkedAccountService(
      LinkedAccountDAO linkedAccountDAO,
      GA4GHPassportDAO ga4ghPassportDAO,
      GA4GHVisaDAO ga4ghVisaDAO,
      ExternalCredsConfig externalCredsConfig,
      ObjectMapper objectMapper) {
    this.linkedAccountDAO = linkedAccountDAO;
    this.ga4ghPassportDAO = ga4ghPassportDAO;
    this.ga4ghVisaDAO = ga4ghVisaDAO;
    this.objectMapper = objectMapper;

    // note that Publisher authenticates to Google using the env var GOOGLE_APPLICATION_CREDENTIALS
    this.authorizationChangeEventPublisher =
        externalCredsConfig
            .getAuthorizationChangeEventTopicName()
            .map(
                topicName -> {
                  try {
                    return Publisher.newBuilder(topicName).build();
                  } catch (IOException e) {
                    throw new ExternalCredsException("exception building event publisher", e);
                  }
                });
  }

  @ReadTransaction
  public Optional<LinkedAccount> getLinkedAccount(int linkedAccountId) {
    return linkedAccountDAO.getLinkedAccount(linkedAccountId);
  }

  @ReadTransaction
  public Optional<LinkedAccount> getLinkedAccount(String userId, String providerId) {
    return linkedAccountDAO.getLinkedAccount(userId, providerId);
  }

  @WriteTransaction
  public LinkedAccountWithPassportAndVisas upsertLinkedAccountWithPassportAndVisas(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas) {
    var savedLinkedAccount =
        linkedAccountDAO.upsertLinkedAccount(linkedAccountWithPassportAndVisas.getLinkedAccount());

    // clear out any passport and visas that may exist and save the new one
    ga4ghPassportDAO.deletePassport(savedLinkedAccount.getId().orElseThrow());

    var savedLinkedAccountWithPassportAndVisas =
        savePassportAndVisasIfPresent(
            linkedAccountWithPassportAndVisas.withLinkedAccount(savedLinkedAccount));

    publishAuthorizationChangeEvent(
        new AuthorizationChangeEvent.Builder()
            .providerId(savedLinkedAccount.getProviderId())
            .userId(savedLinkedAccount.getUserId())
            .build());

    return savedLinkedAccountWithPassportAndVisas;
  }

  @WriteTransaction
  public LinkedAccount upsertLinkedAccount(LinkedAccount linkedAccount) {
    return linkedAccountDAO.upsertLinkedAccount(linkedAccount);
  }

  @WriteTransaction
  public boolean deleteLinkedAccount(String userId, String providerId) {
    var deletionSucceeded = linkedAccountDAO.deleteLinkedAccountIfExists(userId, providerId);
    publishAuthorizationChangeEvent(
        new AuthorizationChangeEvent.Builder().providerId(providerId).userId(userId).build());
    return deletionSucceeded;
  }

  @ReadTransaction
  public List<LinkedAccount> getExpiringLinkedAccounts(Timestamp expirationCutoff) {
    return linkedAccountDAO.getExpiringLinkedAccounts(expirationCutoff);
  }

  private LinkedAccountWithPassportAndVisas savePassportAndVisasIfPresent(
      LinkedAccountWithPassportAndVisas linkedAccountWithPassportAndVisas) {
    if (linkedAccountWithPassportAndVisas.getPassport().isPresent()) {

      var savedPassport =
          ga4ghPassportDAO.insertPassport(
              linkedAccountWithPassportAndVisas
                  .getPassport()
                  .get()
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

  private void publishAuthorizationChangeEvent(AuthorizationChangeEvent event) {
    authorizationChangeEventPublisher.ifPresent(
        publisher -> {
          try {
            var message =
                PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(objectMapper.writeValueAsString(event)))
                    .build();
            var apiFuture = publisher.publish(message);
            ApiFutures.addCallback(
                apiFuture,
                new ApiFutureCallback<>() {
                  @Override
                  public void onFailure(Throwable throwable) {
                    log.error("failure publishing authorization change event", throwable);
                  }

                  @Override
                  public void onSuccess(String messageId) {}
                },
                MoreExecutors.directExecutor());
          } catch (JsonProcessingException e) {
            throw new ExternalCredsException(
                "json exception writing authorization change event:" + event, e);
          }
        });
  }

  @PreDestroy
  void shutdownPublisher() {
    authorizationChangeEventPublisher.ifPresent(
        publisher -> {
          try {
            publisher.shutdown();
            publisher.awaitTermination(1, TimeUnit.MINUTES);
          } catch (InterruptedException e) {
            throw new ExternalCredsException("publisher shutdown interrupted", e);
          }
        });
  }
}

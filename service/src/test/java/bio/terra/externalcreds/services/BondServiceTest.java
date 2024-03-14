package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.BondDatastoreDAO;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.BondFenceServiceAccountEntity;
import bio.terra.externalcreds.models.BondRefreshTokenEntity;
import com.google.cloud.datastore.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class BondServiceTest extends BaseTest {

  @Autowired private BondService bondService;

  @MockBean private ExternalCredsConfig externalCredsConfig;
  @MockBean private BondDatastoreDAO bondDatastoreDAO;

  @Test
  void getLinkedAccount() {
    var entityKey = mock(Key.class);
    var linkedAccount = TestUtils.createRandomLinkedAccount().withProvider(Provider.FENCE);
    var providerProperties = TestUtils.createRandomProvider().setLinkLifespan(Duration.ofDays(30));

    when(externalCredsConfig.getProviderProperties(linkedAccount.getProvider()))
        .thenReturn(providerProperties);
    when(bondDatastoreDAO.getRefreshToken(linkedAccount.getUserId(), linkedAccount.getProvider()))
        .thenReturn(
            Optional.of(
                new BondRefreshTokenEntity.Builder()
                    .token(linkedAccount.getRefreshToken())
                    .issuedAt(
                        linkedAccount
                            .getExpires()
                            .toInstant()
                            .minus(providerProperties.getLinkLifespan()))
                    .username(linkedAccount.getExternalUserId())
                    .key(entityKey)
                    .build()));

    var result =
        bondService.getLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProvider());
    assertPresent(result);
    assertEquals(linkedAccount.getProvider(), result.get().getProvider());
    assertEquals(linkedAccount.getExternalUserId(), result.get().getExternalUserId());
    assertEquals(linkedAccount.getRefreshToken(), result.get().getRefreshToken());
    assertEquals(linkedAccount.getExpires(), result.get().getExpires());
    assertEquals(linkedAccount.getUserId(), result.get().getUserId());
    assertEquals(linkedAccount.isAuthenticated(), result.get().isAuthenticated());
  }

  @Test
  void getFenceServiceAccountKey() {
    var entityKey = mock(Key.class);
    var linkedAccount =
        TestUtils.createRandomLinkedAccount().withProvider(Provider.FENCE).withId(12345);
    var expiresAt = Instant.now().plus(Duration.ofDays(30));
    var keyJson = "{\"key\": \"value\"}";

    when(bondDatastoreDAO.getFenceServiceAccountKey(
            linkedAccount.getUserId(), linkedAccount.getProvider()))
        .thenReturn(
            Optional.of(
                new BondFenceServiceAccountEntity.Builder()
                    .keyJson(keyJson)
                    .expiresAt(expiresAt)
                    .key(entityKey)
                    .build()));

    var result =
        bondService.getFenceServiceAccountKey(
            linkedAccount.getUserId(), linkedAccount.getProvider(), linkedAccount.getId().get());
    assertPresent(result);
    assertEquals(keyJson, result.get().getKeyJson());
    assertEquals(expiresAt, result.get().getExpiresAt());
    assertEquals(linkedAccount.getId().get(), result.get().getLinkedAccountId());
  }

  @Test
  void deleteBondLinkedAccount() {
    var linkedAccount = TestUtils.createRandomLinkedAccount().withProvider(Provider.FENCE);

    bondService.deleteBondLinkedAccount(linkedAccount.getUserId(), linkedAccount.getProvider());
    verify(bondDatastoreDAO)
        .deleteRefreshToken(linkedAccount.getUserId(), linkedAccount.getProvider());
    verify(bondDatastoreDAO)
        .deleteFenceServiceAccountKey(linkedAccount.getUserId(), linkedAccount.getProvider());
  }
}

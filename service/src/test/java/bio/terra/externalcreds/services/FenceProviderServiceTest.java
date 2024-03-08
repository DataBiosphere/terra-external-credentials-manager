package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.TestUtils;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.models.FenceAccountKey;
import bio.terra.externalcreds.models.LinkedAccount;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class FenceProviderServiceTest extends BaseTest {

  @Autowired private FenceProviderService fenceProviderService;

  @MockBean private LinkedAccountService linkedAccountService;
  @MockBean private BondService bondService;
  @MockBean private FenceAccountKeyService fenceAccountKeyService;

  private static final Random random = new Random();

  @Test
  void testGetLinkedFenceAccountKey() {
    var linkedAccountId = random.nextInt();
    var fenceKeyId = random.nextInt();
    var issuedAt = Instant.now();
    var token = "TestToken";
    var userId = UUID.randomUUID().toString();
    var userName = userId + "-name";
    var keyJson = "{ \"name\": \"testKeyJson\"}";
    var provider = Provider.FENCE;

    var linkedAccount =
        new LinkedAccount.Builder()
            .provider(provider)
            .expires(new Timestamp(issuedAt.plus(30, ChronoUnit.DAYS).toEpochMilli()))
            .userId(userId)
            .id(linkedAccountId)
            .externalUserId(userName)
            .refreshToken(token)
            .isAuthenticated(true)
            .build();
    when(linkedAccountService.getLinkedAccount(userId, provider))
        .thenReturn(Optional.of(linkedAccount));

    var fenceAccountKey =
        new FenceAccountKey.Builder()
            .id(fenceKeyId)
            .linkedAccountId(linkedAccountId)
            .keyJson(keyJson)
            .expiresAt(issuedAt.plus(30, ChronoUnit.DAYS))
            .build();
    when(fenceAccountKeyService.getFenceAccountKey(linkedAccount))
        .thenReturn(Optional.of(fenceAccountKey));

    var actualFenceAccountKey = fenceProviderService.getFenceAccountKey(userId, provider);

    assertPresent(actualFenceAccountKey);
  }

  @Nested
  class BondFenceAccounts {

    @Test
    void testGetLinkedFenceAccountExistsInBond() {
      var linkedAccountId = random.nextInt();
      var issuedAt = Instant.now();
      var token = "TestToken";
      var userId = UUID.randomUUID().toString();
      var userName = userId + "-name";
      var keyJson = "{ \"name\": \"testKeyJson\"}";
      var provider = Provider.FENCE;

      var bondLinkedAccount =
          new LinkedAccount.Builder()
              .externalUserId(userName)
              .refreshToken(token)
              .userId(userId)
              .expires(new Timestamp(issuedAt.plus(Duration.ofDays(30)).toEpochMilli()))
              .provider(provider)
              .isAuthenticated(true)
              .build();

      var ecmLinkedAccount = bondLinkedAccount.withId(linkedAccountId);
      when(bondService.getLinkedAccount(userId, provider))
          .thenReturn(Optional.of(bondLinkedAccount));
      when(linkedAccountService.getLinkedAccount(userId, provider)).thenReturn(Optional.empty());

      when(linkedAccountService.upsertLinkedAccount(bondLinkedAccount))
          .thenReturn(ecmLinkedAccount);

      var key =
          new FenceAccountKey.Builder()
              .keyJson(keyJson)
              .expiresAt(issuedAt.plus(Duration.ofDays(30)))
              .linkedAccountId(linkedAccountId)
              .build();

      when(bondService.getFenceServiceAccountKey(userId, provider, linkedAccountId))
          .thenReturn(Optional.of(key));

      var linkedAccount = fenceProviderService.getLinkedFenceAccount(userId, provider);

      assertPresent(linkedAccount);

      when(linkedAccountService.getLinkedAccount(userId, provider))
          .thenReturn(Optional.of(ecmLinkedAccount));
      when(fenceAccountKeyService.getFenceAccountKey(ecmLinkedAccount))
          .thenReturn(Optional.of(key));

      var fenceKey = fenceProviderService.getFenceAccountKey(userId, provider);

      assertPresent(fenceKey);
    }

    @Test
    void testGetLinkedFenceAccountDoesNotExistsInBond() {
      var userId = UUID.randomUUID().toString();
      var provider = Provider.FENCE;

      when(bondService.getLinkedAccount(anyString(), any(Provider.class)))
          .thenReturn(Optional.empty());

      var linkedAccount = fenceProviderService.getLinkedFenceAccount(userId, provider);

      assertEmpty(linkedAccount);
    }

    @Test
    void testGetLinkedFenceAccountExistsInBondAndEcmUpToDate() {
      var linkedAccountId = random.nextInt();
      var issuedAt = Instant.now().minus(Duration.ofDays(90));
      var token = "TestToken";
      var provider = Provider.FENCE;

      var expiration = new Timestamp(Instant.now().plus(Duration.ofDays(30)).toEpochMilli());

      var ecmLinkedAccount =
          TestUtils.createRandomLinkedAccount(Provider.FENCE)
              .withExpires(expiration)
              .withId(linkedAccountId);

      var bondLinkedAccount =
          new LinkedAccount.Builder()
              .provider(provider)
              .expires(new Timestamp(issuedAt.toEpochMilli()))
              .userId(ecmLinkedAccount.getUserId())
              .externalUserId(ecmLinkedAccount.getExternalUserId())
              .refreshToken(token)
              .isAuthenticated(true)
              .build();

      when(linkedAccountService.upsertLinkedAccount(bondLinkedAccount))
          .thenReturn(ecmLinkedAccount);
      when(bondService.getLinkedAccount(anyString(), any(Provider.class)))
          .thenReturn(Optional.of(bondLinkedAccount));

      var linkedAccountFromEcm =
          fenceProviderService.getLinkedFenceAccount(ecmLinkedAccount.getUserId(), provider);

      assertPresent(linkedAccountFromEcm);
      assertEquals(expiration, linkedAccountFromEcm.get().getExpires());
    }

    @Test
    void testGetLinkedFenceAccountExistsInBondAndEcmOutOfDate() {
      var linkedAccountId = random.nextInt();
      var issuedAt = Instant.now();
      var token = "TestToken";
      var provider = Provider.FENCE;

      var expiration = new Timestamp(Instant.now().minus(Duration.ofDays(90)).toEpochMilli());
      var expectedExpiresAt = new Timestamp(issuedAt.plus(30, ChronoUnit.DAYS).toEpochMilli());
      var ecmLinkedAccount =
          TestUtils.createRandomLinkedAccount(Provider.FENCE)
              .withExpires(expiration)
              .withId(linkedAccountId);

      var bondLinkedAccount =
          new LinkedAccount.Builder()
              .provider(provider)
              .expires(expectedExpiresAt)
              .userId(ecmLinkedAccount.getUserId())
              .externalUserId(ecmLinkedAccount.getExternalUserId())
              .refreshToken(token)
              .isAuthenticated(true)
              .build();

      when(linkedAccountService.getLinkedAccount(ecmLinkedAccount.getUserId(), provider))
          .thenReturn(Optional.of(ecmLinkedAccount));
      when(linkedAccountService.upsertLinkedAccount(bondLinkedAccount))
          .thenReturn(bondLinkedAccount.withId(linkedAccountId));
      when(bondService.getLinkedAccount(ecmLinkedAccount.getUserId(), provider))
          .thenReturn(Optional.of(bondLinkedAccount));
      when(bondService.getFenceServiceAccountKey(
              ecmLinkedAccount.getUserId(), provider, linkedAccountId))
          .thenReturn(Optional.empty());

      var linkedAccount =
          fenceProviderService.getLinkedFenceAccount(ecmLinkedAccount.getUserId(), provider);

      assertPresent(linkedAccount);
      assertEquals(expectedExpiresAt, linkedAccount.get().getExpires());
    }
  }
}

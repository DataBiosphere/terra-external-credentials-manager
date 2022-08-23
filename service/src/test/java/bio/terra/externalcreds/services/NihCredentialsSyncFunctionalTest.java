package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GoogleCloudStorageDAO;
import bio.terra.externalcreds.terra.FirecloudOrchestrationClient;
import com.google.cloud.storage.BlobId;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

@Tag("functional")
class NihCredentialsSyncFunctionalTest extends BaseTest {

  @Autowired GoogleCloudStorageDAO googleCloudStorageDAO;
  @Autowired ExternalCredsConfig externalCredsConfig;
  @SpyBean NihCredentialsSyncService nihCredentialsSyncService;
  @MockBean FirecloudOrchestrationClient firecloudOrchestrationClient;

  private final Set<String> allowlistNames =
      Set.of(
          "test-allowlist-1.txt",
          "test-allowlist-2.txt",
          "test-allowlist-3.txt",
          "phs000004.c1.allowlist.txt");

  @BeforeEach
  public void setup() {
    var bucketName = externalCredsConfig.getNihCredentialsSyncConfig().getBucketName();
    var googleProjectId = externalCredsConfig.getNihCredentialsSyncConfig().getGoogleProjectId();
    allowlistNames.forEach(
        l -> {
          googleCloudStorageDAO.writeLinesToBlob(
              googleProjectId,
              BlobId.of(bucketName, l),
              List.of("a\tb\tc", "1\t2\t3", "you\tand\tme"));
        });

    doNothing().when(firecloudOrchestrationClient).syncNihAllowlist(any());
  }

  @Test
  void testGcsIntegrations() {
    assertTrue(nihCredentialsSyncService.allAllowlistsValid());
  }

  @Test
  void testInvalidAllowlists() {
    doReturn(
            nihCredentialsSyncService.createInvalidityFilter(
                Instant.now().plus(13, ChronoUnit.HOURS), Duration.ofHours(12)))
        .when(nihCredentialsSyncService)
        .createInvalidityFilter(any(), any());
    assertFalse(nihCredentialsSyncService.allAllowlistsValid());
  }
}

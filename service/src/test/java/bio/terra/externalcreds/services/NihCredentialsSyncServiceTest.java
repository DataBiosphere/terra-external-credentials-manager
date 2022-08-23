package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.config.NihCredentialsSyncConfig;
import bio.terra.externalcreds.dataAccess.GoogleCloudStorageDAO;
import bio.terra.externalcreds.exception.NihCredentialsSyncException;
import bio.terra.externalcreds.terra.FirecloudOrchestrationClient;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

class NihCredentialsSyncServiceTest extends BaseTest {

  @Nested
  @TestComponent
  class AllTogetherNow {
    @SpyBean NihCredentialsSyncService nihCredentialsSyncService;
    @MockBean GoogleCloudStorageDAO googleCloudStorageDAO;
    @MockBean FirecloudOrchestrationClient firecloudOrchestrationClient;
    @MockBean ExternalCredsConfig externalCredsConfig;
    @Mock NihCredentialsSyncConfig nihCredentialsSyncConfig;
    @Mock Blob validBlob;
    @Mock Blob invalidBlob1;
    @Mock Blob invalidBlob2;

    @BeforeEach
    void setup() throws Exception {
      when(nihCredentialsSyncConfig.getAllowlistManifestPath())
          .thenReturn(Path.of(this.getClass().getResource("/test-nih-allowlists.tsv").toURI()));
      when(nihCredentialsSyncConfig.getAllowlistValidityDuration())
          .thenReturn(Duration.ofHours(12));
      when(nihCredentialsSyncConfig.getBucketName()).thenReturn("fooBucket");
      when(nihCredentialsSyncConfig.getGoogleProjectId()).thenReturn("google-project-id");
      when(externalCredsConfig.getNihCredentialsSyncConfig()).thenReturn(nihCredentialsSyncConfig);

      when(validBlob.getCreateTime())
          .thenReturn(Instant.now().minus(6, ChronoUnit.HOURS).toEpochMilli());
      when(validBlob.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-1.txt"));
      when(invalidBlob1.getCreateTime())
          .thenReturn(Instant.now().minus(13, ChronoUnit.HOURS).toEpochMilli());
      when(invalidBlob1.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-2.txt"));
      when(invalidBlob2.getCreateTime())
          .thenReturn(Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli());
      when(invalidBlob2.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-3.txt"));
      when(googleCloudStorageDAO.streamBlobs(any(), any(), any()))
          .thenAnswer(invocation -> Stream.of(validBlob, invalidBlob1, invalidBlob2));

      doNothing().when(firecloudOrchestrationClient).syncNihAllowlist(any());
      doNothing().when(googleCloudStorageDAO).writeEmptyBlob(any());
      when(nihCredentialsSyncConfig.isFailClosed()).thenReturn(false);
    }

    @Test
    void testFailBasedOnConfig() {
      assertFalse(nihCredentialsSyncService.allAllowlistsValid());
      verify(nihCredentialsSyncService, never()).failClosed(any());

      when(nihCredentialsSyncConfig.isFailClosed()).thenReturn(true);
      assertFalse(nihCredentialsSyncService.allAllowlistsValid());
      verify(nihCredentialsSyncService).failClosed(any());
    }

    @Test
    void testNothingToDo() {
      when(googleCloudStorageDAO.streamBlobs(any(), any(), any()))
          .thenAnswer(invocation -> Stream.of(validBlob));
      assertTrue(nihCredentialsSyncService.allAllowlistsValid());
    }

    @Test
    void testRemediationFailureDoesNotShortCircuit() {
      when(nihCredentialsSyncConfig.isFailClosed()).thenReturn(true);
      doThrow(new RuntimeException("Failed"))
          .when(googleCloudStorageDAO)
          .writeEmptyBlob(invalidBlob1);
      try {
        nihCredentialsSyncService.allAllowlistsValid();
      } catch (RuntimeException e) {
        verify(googleCloudStorageDAO, times(2)).writeEmptyBlob(any());
      }
    }

    @Test
    void testInProcessFailure() {
      when(nihCredentialsSyncConfig.getAllowlistManifestPath())
          .thenReturn(Path.of("Non-existent path"));

      try {
        nihCredentialsSyncService.allAllowlistsValid();
      } catch (NihCredentialsSyncException e) {
        assertEquals("Failed to read manifest of allowlists", e.getMessage());
      }
    }
  }

  @Nested
  @TestComponent
  class InvalidAllowlists {
    @Autowired NihCredentialsSyncService nihCredentialsSyncService;
    @MockBean GoogleCloudStorageDAO googleCloudStorageDAO;
    @MockBean FirecloudOrchestrationClient firecloudOrchestrationClient;
    @MockBean ExternalCredsConfig externalCredsConfig;
    @Mock NihCredentialsSyncConfig nihCredentialsSyncConfig;
    @Mock Blob validBlob;
    @Mock Blob invalidBlob1;
    @Mock Blob invalidBlob2;

    @BeforeEach
    void setup() throws Exception {
      when(nihCredentialsSyncConfig.getAllowlistManifestPath())
          .thenReturn(Path.of(this.getClass().getResource("/test-nih-allowlists.tsv").toURI()));
      when(nihCredentialsSyncConfig.getAllowlistValidityDuration())
          .thenReturn(Duration.ofHours(12));
      when(nihCredentialsSyncConfig.getBucketName()).thenReturn("fooBucket");
      when(nihCredentialsSyncConfig.getGoogleProjectId()).thenReturn("google-project-id");
      when(externalCredsConfig.getNihCredentialsSyncConfig()).thenReturn(nihCredentialsSyncConfig);

      when(validBlob.getCreateTime())
          .thenReturn(Instant.now().minus(6, ChronoUnit.HOURS).toEpochMilli());
      when(validBlob.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-1.txt"));
      when(invalidBlob1.getCreateTime())
          .thenReturn(Instant.now().minus(13, ChronoUnit.HOURS).toEpochMilli());
      when(invalidBlob1.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-2.txt"));
      when(invalidBlob2.getCreateTime())
          .thenReturn(Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli());
      when(invalidBlob2.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-3.txt"));
      when(googleCloudStorageDAO.streamBlobs(any(), any(), any()))
          .thenReturn(Stream.of(validBlob, invalidBlob1, invalidBlob2));
    }

    @Test
    void testGettingInvalidBlobs() {
      Set<Blob> invalidAllowlists = new HashSet<>(nihCredentialsSyncService.getInvalidAllowlists());
      Set<Blob> expectedInvalidLists = new HashSet<>(List.of(invalidBlob1, invalidBlob2));

      assertEquals(expectedInvalidLists, invalidAllowlists);
    }

    @Test
    void testFailClosed() {
      doNothing().when(firecloudOrchestrationClient).syncNihAllowlist(any());
      doNothing().when(googleCloudStorageDAO).writeEmptyBlob(any());

      var invalidLists = List.of(invalidBlob1, invalidBlob2);

      nihCredentialsSyncService.failClosed(invalidLists);

      verify(firecloudOrchestrationClient, times(2)).syncNihAllowlist(any());
      verify(googleCloudStorageDAO, times(2)).writeEmptyBlob(any());
    }
  }

  @Nested
  @TestComponent
  class AllowlistManifestParsing {

    @MockBean ExternalCredsConfig externalCredsConfig;
    @Mock NihCredentialsSyncConfig nihCredentialsSyncConfig;
    @Autowired NihCredentialsSyncService nihCredentialsSyncService;

    @Test
    void testReadingRecordsFromFile() throws Exception {
      when(nihCredentialsSyncConfig.getAllowlistManifestPath())
          .thenReturn(Path.of(this.getClass().getResource("/test-nih-allowlists.tsv").toURI()));
      when(externalCredsConfig.getNihCredentialsSyncConfig()).thenReturn(nihCredentialsSyncConfig);

      var names = nihCredentialsSyncService.getAllowListFileNames();
      assertEquals(names.size(), 4);
      assertEquals(
          Set.of(
              "test-allowlist-1.txt",
              "test-allowlist-2.txt",
              "test-allowlist-3.txt",
              "phs000004.c1.allowlist.txt"),
          names);
    }
  }

  @Nested
  @TestComponent
  class InvalidityFilter {
    @Mock Blob testBlob;

    @Test
    void testInvalidityFilterMatchesOldAllowlists() {
      when(testBlob.getCreateTime())
          .thenReturn(Instant.now().minus(13, ChronoUnit.HOURS).toEpochMilli());
      Predicate<Blob> validityFilter =
          NihCredentialsSyncService.createInvalidityFilter(Instant.now(), Duration.ofHours(12));
      assertTrue(validityFilter.test(testBlob));

      var savedNow = Instant.now().truncatedTo(ChronoUnit.MILLIS);
      var savedMinusTwelve = savedNow.minus(12, ChronoUnit.HOURS);
      when(testBlob.getCreateTime()).thenReturn(savedMinusTwelve.toEpochMilli());
      validityFilter =
          NihCredentialsSyncService.createInvalidityFilter(savedNow, Duration.ofHours(12));
      assertTrue(validityFilter.test(testBlob));
    }

    @Test
    void testInvalidityFilterExcludesValidAllowlists() {
      when(testBlob.getCreateTime())
          .thenReturn(Instant.now().minus(6, ChronoUnit.HOURS).toEpochMilli());
      Predicate<Blob> validityFilter =
          NihCredentialsSyncService.createInvalidityFilter(Instant.now(), Duration.ofHours(12));
      assertFalse(validityFilter.test(testBlob));
    }
  }
}

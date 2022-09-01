package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import bio.terra.externalcreds.models.NihCredentialsBlob;
import bio.terra.externalcreds.models.NihCredentialsManifestEntry;
import bio.terra.externalcreds.terra.FirecloudOrchestrationClient;
import bio.terra.externalcreds.util.EcmRestTemplate;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@Tag("unit")
class NihCredentialsSyncServiceTest extends BaseTest {

  @Nested
  @TestComponent
  class AllTogetherNow {
    @SpyBean NihCredentialsSyncService nihCredentialsSyncService;
    @Autowired FirecloudOrchestrationClient firecloudOrchestrationClient;
    @MockBean GoogleCloudStorageDAO googleCloudStorageDAO;
    @MockBean EcmRestTemplate restTemplate;
    @MockBean ExternalCredsConfig externalCredsConfig;
    @Mock NihCredentialsSyncConfig nihCredentialsSyncConfig;
    @Mock Blob validBlob;
    @Mock Blob invalidBlob1;
    @Mock Blob invalidBlob2;

    @BeforeEach
    void setup() throws Exception {
      when(nihCredentialsSyncConfig.getAllowlistManifestPath()).thenReturn("foo");
      when(nihCredentialsSyncConfig.getAllowlistValidityDuration())
          .thenReturn(Duration.ofHours(12));
      when(nihCredentialsSyncConfig.getBucketName()).thenReturn("fooBucket");
      when(nihCredentialsSyncConfig.getGoogleProjectId()).thenReturn("google-project-id");
      when(externalCredsConfig.getNihCredentialsSyncConfig()).thenReturn(nihCredentialsSyncConfig);

      when(validBlob.getCreateTime())
          .thenReturn(Instant.now().minus(6, ChronoUnit.HOURS).toEpochMilli());
      when(validBlob.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-1.txt"));
      when(validBlob.getName()).thenReturn("test-allowlist-1.txt");
      when(invalidBlob1.getCreateTime())
          .thenReturn(Instant.now().minus(13, ChronoUnit.HOURS).toEpochMilli());
      when(invalidBlob1.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-2.txt"));
      when(invalidBlob1.getName()).thenReturn("test-allowlist-2.txt");
      when(invalidBlob2.getCreateTime())
          .thenReturn(Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli());
      when(invalidBlob2.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-3.txt"));
      when(invalidBlob2.getName()).thenReturn("test-allowlist-3.txt");
      when(googleCloudStorageDAO.streamBlobs(any(), any()))
          .thenAnswer(invocation -> Stream.of(validBlob, invalidBlob1, invalidBlob2));
      when(googleCloudStorageDAO.readLinesFromBlob(any(), any()))
          .thenReturn(
              Files.readAllLines(
                  Path.of(this.getClass().getResource("/test-nih-allowlists.tsv").toURI())));

      when(restTemplate.exchange(
              any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class)))
          .thenReturn(ResponseEntity.ok(new Object()));
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
      when(googleCloudStorageDAO.streamBlobs(any(), any()))
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
      when(googleCloudStorageDAO.readLinesFromBlob(any(), any()))
          .thenThrow(new RuntimeException("Failure"));

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
    @Mock bio.terra.externalcreds.models.NihCredentialsBlob validNihBlob;
    @Mock NihCredentialsBlob invalidNihBlob1;
    @Mock NihCredentialsBlob invalidNihBlob2;

    @BeforeEach
    void setup() throws Exception {
      when(nihCredentialsSyncConfig.getAllowlistManifestPath()).thenReturn("foo");
      when(nihCredentialsSyncConfig.getAllowlistValidityDuration())
          .thenReturn(Duration.ofHours(12));
      when(nihCredentialsSyncConfig.getBucketName()).thenReturn("fooBucket");
      when(nihCredentialsSyncConfig.getGoogleProjectId()).thenReturn("google-project-id");
      when(externalCredsConfig.getNihCredentialsSyncConfig()).thenReturn(nihCredentialsSyncConfig);

      when(validNihBlob.blob()).thenReturn(validBlob);
      when(validNihBlob.entry())
          .thenReturn(
              new NihCredentialsManifestEntry(
                  "TEST_ALLOWLIST_1",
                  "test_authentication_file_phs000001.txt.enc",
                  "test-allowlist-1.txt",
                  " c1"));
      when(validBlob.getCreateTime())
          .thenReturn(Instant.now().minus(6, ChronoUnit.HOURS).toEpochMilli());
      when(validBlob.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-1.txt"));
      when(validBlob.getName()).thenReturn("test-allowlist-1.txt");

      when(invalidNihBlob1.blob()).thenReturn(invalidBlob1);
      when(invalidNihBlob1.entry())
          .thenReturn(
              new NihCredentialsManifestEntry(
                  "TEST_ALLOWLIST_2",
                  "test_authentication_file_phs000002.txt.enc",
                  "test-allowlist-2.txt",
                  "c1"));
      when(invalidBlob1.getCreateTime())
          .thenReturn(Instant.now().minus(13, ChronoUnit.HOURS).toEpochMilli());
      when(invalidBlob1.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-2.txt"));
      when(invalidBlob1.getName()).thenReturn("test-allowlist-2.txt");

      when(invalidNihBlob2.blob()).thenReturn(invalidBlob2);
      when(invalidNihBlob2.entry())
          .thenReturn(
              new NihCredentialsManifestEntry(
                  "TEST_ALLOWLIST_3",
                  "test_authentication_file_phs000003.txt.enc",
                  "test-allowlist-3.txt",
                  "c1"));
      when(invalidBlob2.getCreateTime())
          .thenReturn(Instant.now().minus(24, ChronoUnit.HOURS).toEpochMilli());
      when(invalidBlob2.getBlobId()).thenReturn(BlobId.of("fooBucket", "test-allowlist-3.txt"));
      when(invalidBlob2.getName()).thenReturn("test-allowlist-3.txt");

      when(googleCloudStorageDAO.readLinesFromBlob(any(), any()))
          .thenReturn(
              Files.readAllLines(
                  Path.of(this.getClass().getResource("/test-nih-allowlists.tsv").toURI())));
      when(googleCloudStorageDAO.streamBlobs(any(), any()))
          .thenReturn(Stream.of(validBlob, invalidBlob1, invalidBlob2));
    }

    @Test
    void testGettingInvalidBlobs() {
      Set<Blob> invalidAllowlists =
          nihCredentialsSyncService.getInvalidAllowlists().stream()
              .map(NihCredentialsBlob::blob)
              .collect(Collectors.toSet());
      Set<Blob> expectedInvalidLists = new HashSet<>(List.of(invalidBlob1, invalidBlob2));

      assertEquals(expectedInvalidLists, invalidAllowlists);
    }

    @Test
    void testFailClosed() {
      doNothing().when(firecloudOrchestrationClient).syncNihAllowlist(any());
      doNothing().when(googleCloudStorageDAO).writeEmptyBlob(any());

      var invalidLists = List.of(invalidNihBlob1, invalidNihBlob2);

      nihCredentialsSyncService.failClosed(invalidLists);

      verify(firecloudOrchestrationClient, times(2)).syncNihAllowlist(any());
      verify(googleCloudStorageDAO, times(2)).writeEmptyBlob(any());
    }
  }

  @Nested
  @TestComponent
  class AllowlistManifestParsing {

    @MockBean GoogleCloudStorageDAO googleCloudStorageDAO;
    @Autowired NihCredentialsSyncService nihCredentialsSyncService;

    @Test
    void testReadingRecordsFromFile() throws Exception {
      when(googleCloudStorageDAO.readLinesFromBlob(any(), any()))
          .thenReturn(
              Files.readAllLines(
                  Path.of(this.getClass().getResource("/test-nih-allowlists.tsv").toURI())));
      var entries = nihCredentialsSyncService.getAllowListManifestEntries();
      assertEquals(4, entries.size());
      assertEquals(
          Set.of(
              "test-allowlist-1.txt",
              "test-allowlist-2.txt",
              "test-allowlist-3.txt",
              "phs000004.c1.allowlist.txt"),
          entries.stream()
              .map(NihCredentialsManifestEntry::outputFile)
              .collect(Collectors.toSet()));
    }
  }

  @Nested
  @TestComponent
  class InvalidityFilter {
    @Autowired NihCredentialsSyncService nihCredentialsSyncService;
    @Mock Blob testBlob;
    @Mock NihCredentialsBlob testNihBlob;

    @Test
    void testInvalidityFilterMatchesOldAllowlists() {
      when(testBlob.getCreateTime())
          .thenReturn(Instant.now().minus(13, ChronoUnit.HOURS).toEpochMilli());
      when(testNihBlob.blob()).thenReturn(testBlob);
      Predicate<NihCredentialsBlob> validityFilter =
          nihCredentialsSyncService.createInvalidityFilter(Instant.now(), Duration.ofHours(12));
      assertTrue(validityFilter.test(testNihBlob));

      var savedNow = Instant.now().truncatedTo(ChronoUnit.MILLIS);
      var savedMinusTwelve = savedNow.minus(12, ChronoUnit.HOURS);
      when(testBlob.getCreateTime()).thenReturn(savedMinusTwelve.toEpochMilli());
      validityFilter =
          nihCredentialsSyncService.createInvalidityFilter(savedNow, Duration.ofHours(12));
      assertTrue(validityFilter.test(testNihBlob));
    }

    @Test
    void testInvalidityFilterExcludesValidAllowlists() {
      when(testBlob.getCreateTime())
          .thenReturn(Instant.now().minus(6, ChronoUnit.HOURS).toEpochMilli());
      when(testNihBlob.blob()).thenReturn(testBlob);
      Predicate<NihCredentialsBlob> validityFilter =
          nihCredentialsSyncService.createInvalidityFilter(Instant.now(), Duration.ofHours(12));
      assertFalse(validityFilter.test(testNihBlob));
    }
  }
}

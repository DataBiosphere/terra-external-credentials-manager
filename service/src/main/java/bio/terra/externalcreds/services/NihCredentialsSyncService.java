package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GoogleCloudStorageDAO;
import bio.terra.externalcreds.exception.NihCredentialsSyncException;
import bio.terra.externalcreds.models.NihCredentialsManifestEntry;
import bio.terra.externalcreds.terra.FirecloudOrchestrationClient;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NihCredentialsSyncService {

  private ExternalCredsConfig externalCredsConfig;
  private FirecloudOrchestrationClient firecloudOrchestrationClient;
  private GoogleCloudStorageDAO googleCloudStorageDAO;

  public NihCredentialsSyncService(
      ExternalCredsConfig externalCredsConfig,
      FirecloudOrchestrationClient firecloudOrchestrationClient,
      GoogleCloudStorageDAO googleCloudStorageDAO) {
    this.externalCredsConfig = externalCredsConfig;
    this.firecloudOrchestrationClient = firecloudOrchestrationClient;
    this.googleCloudStorageDAO = googleCloudStorageDAO;
  }

  /**
   * Check that all allowlists published by the NIH are still recent enough to be considered valid.
   * The validity period is set in configs at
   * `externalcreds.nih-credentials-sync-config.allowlist-validity-duration`
   *
   * <p>If `externalcreds.nih-credentials-sync-config.is-fail-closed` is set to true, then invalid
   * allowlists will be overwritten with empty files, and Firecloud Orch will be told to update the
   * Sam permissions, which will wipe out all access for the data governed by the allowlist.
   *
   * @return if all allowlists are valid or not
   */
  public boolean allAllowlistsValid() {
    var invalidAllowlists = getInvalidAllowlists();
    if (invalidAllowlists.isEmpty()) {
      log.info("No NIH allowlists are outside of their validity period");
      return true;
    }
    log.error("NIH allowlists outside of their validity period found!");
    var now = Instant.now();
    invalidAllowlists.forEach(
        b -> {
          var between = Duration.between(Instant.ofEpochMilli(b.getCreateTime()), now);
          log.error(
              "NIH allowlist {} is {} hours and {} minutes old",
              b.getBlobId().getName(),
              between.toHoursPart(),
              between.toMinutesPart());
        });
    if (externalCredsConfig.getNihCredentialsSyncConfig().isFailClosed()) {
      log.warn("Config set to fail-closed. Removing all access for invalid allowlists");
      var allAllowlistsWiped = failClosed(invalidAllowlists);
      if (!allAllowlistsWiped) {
        throw new NihCredentialsSyncException(
            "SOME PERMISSION-REVOCATION OF ALLOWLISTS FAILED! "
                + "Check previous logs for lists that need manual intervention.");
      }
    } else {
      log.error(
          "Config not set to fail-closed. Doing nothing. Someone should definitely check this out");
    }
    return false;
  }

  /**
   * Take the invalid allowlists, and update permissions so that nobody can access the data.
   *
   * @param invalidAllowlists allowlists that are too old to be valid anymore.
   * @return if all the invalid allowlists could be updated and permissions revoked.
   */
  boolean failClosed(List<Blob> invalidAllowlists) {
    return invalidAllowlists.stream()
        .map(
            b -> {
              log.warn(
                  "Overwriting allowlist {} and telling Orch to sync", b.getBlobId().toGsUtilUri());
              var name = b.getName();
              try {
                googleCloudStorageDAO.writeEmptyBlob(b);
                firecloudOrchestrationClient.syncNihAllowlist(name);
                log.info("Successfully cleared all access for allowlist {}", name);
              } catch (Exception e) {
                log.error(
                    "Failed to clear access for allowlist {}. THERE IS A POTENTIAL FOR UNAUTHORIZED ACCESS",
                    name);
                return false;
              }
              return true;
            })
        .reduce(true, Boolean::logicalAnd);
  }

  /**
   * Based on allowlists determined by the manifest defined in config, and allowlists existing in
   * GCS, find any allowlists that are no longer valid because they are too old.
   *
   * @return List of blobs pointing to invalid allowlists.
   */
  List<Blob> getInvalidAllowlists() {
    var googleProjectId = externalCredsConfig.getNihCredentialsSyncConfig().getGoogleProjectId();
    var bucketName = externalCredsConfig.getNihCredentialsSyncConfig().getBucketName();
    var allowlistValidityDuration =
        externalCredsConfig.getNihCredentialsSyncConfig().getAllowlistValidityDuration();
    var invalidityFilter = createInvalidityFilter(Instant.now(), allowlistValidityDuration);

    try (var blobsStream =
        googleCloudStorageDAO.streamBlobs(
            getStorage(googleProjectId), bucketName, Optional.empty())) {
      var blobsToCheck = getCurrentBlobs(blobsStream);
      return blobsToCheck.filter(invalidityFilter).collect(Collectors.toList());
    }
  }

  /**
   * Filter down all blobs in the bucket to just the ones that appear in the manifest.
   *
   * @param allBlobsInBucket
   * @return only the allowlists appearing in the configured manifest
   */
  Stream<Blob> getCurrentBlobs(Stream<Blob> allBlobsInBucket) {
    var validAllowlists = getAllowListFileNames();
    return allBlobsInBucket.filter(b -> validAllowlists.contains(b.getBlobId().getName()));
  }

  // Get all the allowlists from the configured manifest
  Set<String> getAllowListFileNames() {
    try {
      return Files.readAllLines(
              externalCredsConfig.getNihCredentialsSyncConfig().getAllowlistManifestPath())
          .stream()
          .map(NihCredentialsManifestEntry::fromManifestLine)
          .map(NihCredentialsManifestEntry::outputFile)
          .collect(Collectors.toSet());
    } catch (IOException e) {
      throw new NihCredentialsSyncException("Failed to read manifest of allowlists", e);
    }
  }

  // Splitting this into its own method for unit-testability
  static Predicate<Blob> createInvalidityFilter(Instant now, Duration allowlistValidityDuration) {
    return (Blob b) ->
        !Duration.between(Instant.ofEpochMilli(b.getCreateTime()), now)
            .minus(allowlistValidityDuration)
            .isNegative();
  }

  // Get a Storage with application-default credentials
  private static Storage getStorage(String googleProjectId) {
    HttpTransportOptions transportOptions = StorageOptions.getDefaultHttpTransportOptions();
    transportOptions =
        transportOptions.toBuilder()
            .setConnectTimeout(GoogleCloudStorageDAO.CONNECT_TIMEOUT_SECONDS * 1000)
            .setReadTimeout(GoogleCloudStorageDAO.READ_TIMEOUT_SECONDS * 1000)
            .build();

    try {
      var credentials = GoogleCredentials.getApplicationDefault();

      StorageOptions storageOptions =
          StorageOptions.newBuilder()
              .setTransportOptions(transportOptions)
              .setProjectId(googleProjectId)
              .setCredentials(credentials)
              .build();

      return storageOptions.getService();
    } catch (IOException e) {
      throw new NihCredentialsSyncException("Could not get google credentials for GCS access", e);
    }
  }
}

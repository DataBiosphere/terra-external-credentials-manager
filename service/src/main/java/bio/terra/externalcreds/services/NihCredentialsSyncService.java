package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.GoogleCloudStorageDAO;
import bio.terra.externalcreds.exception.NihCredentialsSyncException;
import bio.terra.externalcreds.models.NihCredentialsBlob;
import bio.terra.externalcreds.models.NihCredentialsManifestEntry;
import bio.terra.externalcreds.terra.FirecloudOrchestrationClient;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NihCredentialsSyncService {

  private final ExternalCredsConfig externalCredsConfig;
  private final FirecloudOrchestrationClient firecloudOrchestrationClient;
  private final GoogleCloudStorageDAO googleCloudStorageDAO;

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
          var between = Duration.between(Instant.ofEpochMilli(b.blob().getCreateTime()), now);
          log.error(
              "NIH allowlist {} is {} hours and {} minutes old",
              b.blob().getBlobId().getName(),
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
  boolean failClosed(List<NihCredentialsBlob> invalidAllowlists) {
    return invalidAllowlists.stream()
        .map(
            b -> {
              log.warn(
                  "Overwriting allowlist {} and telling Orch to sync",
                  b.blob().getBlobId().toGsUtilUri());
              var blobName = b.blob().getName();
              try {
                googleCloudStorageDAO.writeEmptyBlob(b.blob());
                firecloudOrchestrationClient.syncNihAllowlist(b.entry().name());
                log.info("Successfully cleared all access for allowlist {}", b.blob().getName());
              } catch (Exception e) {
                log.error(
                    "Failed to clear access for allowlist {}. THERE IS A POTENTIAL FOR UNAUTHORIZED ACCESS",
                    blobName,
                    e);
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
  List<NihCredentialsBlob> getInvalidAllowlists() {
    var bucketName = externalCredsConfig.getNihCredentialsSyncConfig().getBucketName();
    var googleProjectId = externalCredsConfig.getNihCredentialsSyncConfig().getGoogleProjectId();
    var allowlistValidityDuration =
        externalCredsConfig.getNihCredentialsSyncConfig().getAllowlistValidityDuration();
    var invalidityFilter = createInvalidityFilter(Instant.now(), allowlistValidityDuration);

    try (var blobsStream = googleCloudStorageDAO.streamBlobs(googleProjectId, bucketName)) {
      var blobsToCheck = getCurrentBlobs(blobsStream);
      return blobsToCheck.filter(invalidityFilter).toList();
    }
  }

  /**
   * Filter down all blobs in the bucket to just the ones that appear in the manifest.
   *
   * @param allBlobsInBucket
   * @return only the allowlists appearing in the configured manifest
   */
  Stream<NihCredentialsBlob> getCurrentBlobs(Stream<Blob> allBlobsInBucket) {
    var validAllowlists = getAllowListManifestEntries();
    var outputFileToEntry =
        validAllowlists.stream()
            .collect(
                Collectors.toMap(NihCredentialsManifestEntry::outputFile, Function.identity()));
    var outputFileNames = outputFileToEntry.keySet();
    return allBlobsInBucket
        .filter(b -> outputFileNames.contains(b.getName()))
        .map(b -> outputFileToEntry.get(b.getName()).withBlob(b));
  }

  // Get all the allowlists from the configured manifest in the cloud bucket
  Set<NihCredentialsManifestEntry> getAllowListManifestEntries() {
    var googleProjectId = externalCredsConfig.getNihCredentialsSyncConfig().getGoogleProjectId();
    var bucketName = externalCredsConfig.getNihCredentialsSyncConfig().getBucketName();
    var manifestPath = externalCredsConfig.getNihCredentialsSyncConfig().getAllowlistManifestPath();
    try {
      return googleCloudStorageDAO
          .readLinesFromBlob(googleProjectId, BlobId.of(bucketName, manifestPath))
          .stream()
          .map(NihCredentialsManifestEntry::fromManifestLine)
          .collect(Collectors.toSet());
    } catch (Exception e) {
      throw new NihCredentialsSyncException("Failed to read manifest of allowlists", e);
    }
  }

  // Splitting this into its own method for unit-testability
  Predicate<NihCredentialsBlob> createInvalidityFilter(
      Instant now, Duration allowlistValidityDuration) {
    return (NihCredentialsBlob b) ->
        !Duration.between(Instant.ofEpochMilli(b.blob().getCreateTime()), now)
            .minus(allowlistValidityDuration)
            .isNegative();
  }
}

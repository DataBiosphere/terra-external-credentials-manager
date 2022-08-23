package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.exception.NihCredentialsSyncException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class GoogleCloudStorageDAO {

  public static final int CONNECT_TIMEOUT_SECONDS = 20;
  public static final int READ_TIMEOUT_SECONDS = 40;

  private final Map<String, Storage> storageMap = new PassiveExpiringMap<>(15, TimeUnit.MINUTES);
  /**
   * It is important that the stream returned by this method be guaranteed to closed. Since this is
   * a stream from IO, we need to make sure that the handle is closed. This can be done by wrapping
   * the stream in a try-block once the stream is used for a terminal operation.
   */
  public Stream<Blob> streamBlobs(
      String googleProjectId, String bucketName, Optional<String> prefixFilter) {
    var storage = getStorage(googleProjectId);
    final Storage.BlobListOption[] listOptions;
    if (prefixFilter.isPresent()) {
      listOptions =
          new Storage.BlobListOption[] {
            prefixFilter.map(Storage.BlobListOption::prefix).get(),
            Storage.BlobListOption.currentDirectory()
          };
    } else {
      listOptions = new Storage.BlobListOption[] {Storage.BlobListOption.currentDirectory()};
    }
    Iterable<Blob> blobs = storage.list(bucketName, listOptions).iterateAll();
    return StreamSupport.stream(blobs.spliterator(), false);
  }

  public void writeEmptyBlob(Blob blob) {
    try (var writer = blob.writer()) {
      writer.write(ByteBuffer.wrap("".getBytes(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new ExternalCredsException(
          String.format("Failed to write empty blob at %s", blob.getBlobId().toGsUtilUri()), e);
    }
  }

  /**
   * Write a {@link List<String>} to a GCS blob separated by newlines
   *
   * @param blobId blob id to write the lines to
   * @param contentsToWrite contents to write to file
   */
  public void writeLinesToBlob(
      String googleProjectId, BlobId blobId, List<String> contentsToWrite) {
    var newLine = "\n".getBytes(StandardCharsets.UTF_8);
    var storage = getStorage(googleProjectId);
    Blob blob = storage.get(blobId);
    if (blob == null) {
      blob = storage.create(BlobInfo.newBuilder(blobId).build());
    }
    try (var writer = blob.writer()) {
      contentsToWrite.forEach(
          s -> {
            try {
              writer.write(ByteBuffer.wrap(s.getBytes(StandardCharsets.UTF_8)));
              writer.write(ByteBuffer.wrap(newLine));
            } catch (IOException e) {
              throw new ExternalCredsException(
                  String.format("Could not write to blob at %s. Line: %s", blobId.toGsUtilUri(), s),
                  e);
            }
          });
    } catch (IOException ex) {
      throw new ExternalCredsException(
          String.format("Could not write to blob at %s", blobId.toGsUtilUri()), ex);
    }
  }

  public List<String> readLinesFromBlob(String googleProjectId, BlobId blobId) {
    var storage = getStorage(googleProjectId);
    return List.of(new String(storage.readAllBytes(blobId), StandardCharsets.UTF_8).split("\n"));
  }

  // Get a Storage with application-default credentials
  @VisibleForTesting
  public Storage getStorage(String googleProjectId) {
    return storageMap.computeIfAbsent(
        googleProjectId,
        p -> {
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
                    .setProjectId(p)
                    .setCredentials(credentials)
                    .build();

            return storageOptions.getService();
          } catch (IOException e) {
            throw new NihCredentialsSyncException(
                "Could not get google credentials for GCS access", e);
          }
        });
  }
}

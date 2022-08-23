package bio.terra.externalcreds.dataAccess;

import bio.terra.externalcreds.ExternalCredsException;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

@Repository
@Slf4j
public class GoogleCloudStorageDAO {

  public static int CONNECT_TIMEOUT_SECONDS = 20;
  public static int READ_TIMEOUT_SECONDS = 40;
  /**
   * It is important that the stream returned by this method be guaranteed to closed. Since this is
   * a stream from IO, we need to make sure that the handle is closed. This can be done by wrapping
   * the stream in a try-block once the stream is used for a terminal operation.
   */
  public Stream<Blob> streamBlobs(
      Storage storage, String bucketName, Optional<String> prefixFilter) {
    final Storage.BlobListOption[] listOptions;
    if (prefixFilter.isPresent()) {
      listOptions =
          new Storage.BlobListOption[] {
            prefixFilter.map(Storage.BlobListOption::prefix).get(),
            Storage.BlobListOption.currentDirectory()
          };
    } else {
      listOptions = new Storage.BlobListOption[0];
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
}

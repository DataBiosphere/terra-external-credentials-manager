package bio.terra.externalcreds.config;

import java.nio.file.Path;
import java.time.Duration;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
public interface NihCredentialsSyncConfigInterface {

  Duration getAllowlistValidityDuration();

  Path getAllowlistManifestPath();

  String getGoogleProjectId();

  String getBucketName();

  boolean isFailClosed();
}

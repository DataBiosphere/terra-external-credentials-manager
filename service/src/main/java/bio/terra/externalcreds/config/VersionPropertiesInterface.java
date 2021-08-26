package bio.terra.externalcreds.config;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import org.immutables.value.Value;

@Value.Modifiable
@PropertiesInterfaceStyle
@JsonIncludeProperties({"gitTag", "gitHash", "github", "build"})
public interface VersionPropertiesInterface {
  String getGitTag();

  String getGitHash();

  String getGithub();

  String getBuild();
}

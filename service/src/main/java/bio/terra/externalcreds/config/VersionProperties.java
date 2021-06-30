package bio.terra.externalcreds.config;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("version")
@Getter
@Setter
@JsonIncludeProperties({"gitTag", "gitHash", "github", "build"})
public class VersionProperties {
  private String gitTag;
  private String gitHash;
  private String github;
  private String build;
}

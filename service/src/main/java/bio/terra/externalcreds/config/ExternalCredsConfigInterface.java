package bio.terra.externalcreds.config;

import java.util.Map;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@Value.Modifiable
@Value.Style(typeImmutable = "*", typeAbstract = "*Interface", typeModifiable = "*")
public interface ExternalCredsConfigInterface {
  Map<String, ProviderProperties> getProviders();

  // Nullable to make the generated class play nicely with spring: spring likes to call the getter
  // before the setter and without Nullable the immutables generated code errors because the field
  // is not set yet. Spring does not seem to recognize Optional.
  @Nullable
  VersionProperties getVersion();

  String getSamBasePath();
}

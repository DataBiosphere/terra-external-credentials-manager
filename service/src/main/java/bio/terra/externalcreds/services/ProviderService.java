package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ProviderConfig;
import java.util.Collections;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProviderService {

  private final ProviderConfig providerConfig;

  public ProviderService(ProviderConfig providerConfig) {
    this.providerConfig = providerConfig;
  }

  public Set<String> getProviderList() {
    return Collections.unmodifiableSet(providerConfig.getServices().keySet());
  }
}

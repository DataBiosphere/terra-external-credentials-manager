package bio.terra.externalcreds.services;

import bio.terra.externalcreds.config.ProviderConfig;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProviderService {

  private final ProviderConfig providerConfig;

  public ProviderService(ProviderConfig providerConfig) {
    this.providerConfig = providerConfig;
  }

  public List<String> getProviderList() {
    return List.copyOf(providerConfig.getServices().keySet());
  }
}

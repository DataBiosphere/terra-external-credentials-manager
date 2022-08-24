package bio.terra.externalcreds.terra;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.util.EcmRestTemplate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FirecloudOrchestrationClient {

  private final ExternalCredsConfig externalCredsConfig;
  private final EcmRestTemplate restTemplate;
  private final HttpHeaders headers;

  public FirecloudOrchestrationClient(
      ExternalCredsConfig externalCredsConfig, EcmRestTemplate restTemplate) {
    this.externalCredsConfig = externalCredsConfig;
    this.restTemplate = restTemplate;
    this.headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
  }

  public void syncNihAllowlist(String allowlistName) {
    ResponseEntity<Object> response =
        restTemplate.exchange(
            String.format(
                "%s/api/nih/sync_whitelist/%s",
                externalCredsConfig.getOrchestrationBasePath(), allowlistName),
            HttpMethod.POST,
            new HttpEntity<>(headers),
            Object.class);
    if (response.getStatusCode().isError()) {
      log.error(String.format("Error response from orch: %s", response));
      throw new ExternalCredsException(
          String.format("Failed to tell orchestration to sync NIH allowlist %s", allowlistName));
    }
  }
}

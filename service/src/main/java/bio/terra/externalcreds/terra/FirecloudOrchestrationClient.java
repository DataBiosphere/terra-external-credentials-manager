package bio.terra.externalcreds.terra;

import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class FirecloudOrchestrationClient {

  private final ExternalCredsConfig externalCredsConfig;
  private final RestTemplate restTemplate;
  private final HttpHeaders headers;

  @Autowired
  public FirecloudOrchestrationClient(ExternalCredsConfig externalCredsConfig) {
    this.externalCredsConfig = externalCredsConfig;
    this.restTemplate = new RestTemplate();
    restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
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

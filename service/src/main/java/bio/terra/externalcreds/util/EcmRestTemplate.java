package bio.terra.externalcreds.util;

import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class EcmRestTemplate extends RestTemplate {

  public EcmRestTemplate() {
    this.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
  }
}

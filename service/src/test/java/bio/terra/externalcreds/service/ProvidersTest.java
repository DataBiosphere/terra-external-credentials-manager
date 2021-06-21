package bio.terra.externalcreds.service;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.config.ProviderConfig;
import bio.terra.externalcreds.config.ProviderConfig.ProviderInfo;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

public class ProvidersTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockBean private ProviderConfig providerConfig;

  @Test
  void testListProviders() throws Exception {
    when(providerConfig.getServices())
        .thenReturn(
            Map.of("fake-provider", new ProviderInfo(), "fake-provider2", new ProviderInfo()));

    mvc.perform(get("/api/oidc/v1/providers"))
        .andExpect(content().json("[\"fake-provider\",\"fake-provider2\"]"));
  }
}

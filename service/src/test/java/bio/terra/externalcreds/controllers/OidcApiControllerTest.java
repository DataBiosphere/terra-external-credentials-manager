package bio.terra.externalcreds.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.services.ProviderService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class OidcApiControllerTest extends BaseTest {

  @Autowired private MockMvc mvc;

  @MockBean private ProviderService providerService;

  @Test
  void testListProviders() throws Exception {
    when(providerService.getProviderList()).thenReturn(Set.of("fake-provider2", "fake-provider1"));

    mvc.perform(get("/api/oidc/v1/providers"))
        .andExpect(content().json("[\"fake-provider1\",\"fake-provider2\"]"));
  }

  @Test
  void testGetAuthUrl() throws Exception {
    String result = "https://test/authorization/uri";
    String provider = "fake";
    String redirectUri = "fakeuri";
    Set<String> scopes = Set.of("openid", "email");
    String state = null;

    when(providerService.getProviderAuthorizationUrl(provider, redirectUri, scopes, state))
        .thenReturn(result);

    MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
    queryParams.add("redirectUri", redirectUri);
    queryParams.addAll("scopes", List.copyOf(scopes));
    mvc.perform(get("/api/oidc/v1/{provider}/authorization-url", provider).queryParams(queryParams))
        .andExpect(content().json("\"" + result + "\""));
  }

  @Test
  void testGetAuthUrl404() throws Exception {
    String provider = "fake";
    String redirectUri = "fakeuri";
    Set<String> scopes = Set.of("openid", "email");
    String state = null;

    when(providerService.getProviderAuthorizationUrl(provider, redirectUri, scopes, state))
        .thenReturn(null);

    MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
    queryParams.add("redirectUri", redirectUri);
    queryParams.addAll("scopes", List.copyOf(scopes));
    mvc.perform(get("/api/oidc/v1/{provider}/authorization-url", provider).queryParams(queryParams))
        .andExpect(status().isNotFound());
  }
}

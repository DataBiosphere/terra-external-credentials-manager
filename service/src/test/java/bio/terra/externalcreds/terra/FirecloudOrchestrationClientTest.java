package bio.terra.externalcreds.terra;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.ExternalCredsException;
import bio.terra.externalcreds.util.EcmRestTemplate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@Tag("unit")
class FirecloudOrchestrationClientTest extends BaseTest {

  @Autowired private FirecloudOrchestrationClient firecloudOrchestrationClient;
  @MockBean private EcmRestTemplate restTemplate;

  @Test
  void testSuccessfulNihAllowlistCall() {
    when(restTemplate.exchange(
            any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class)))
        .thenReturn(ResponseEntity.ok(new Object()));
    assertDoesNotThrow(() -> firecloudOrchestrationClient.syncNihAllowlist("foobar"));
  }

  @Test
  void testFailedNihAllowlistCall() {
    when(restTemplate.exchange(
            any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class)))
        .thenReturn(ResponseEntity.internalServerError().body(new Object()));

    assertThrows(
        ExternalCredsException.class,
        () -> firecloudOrchestrationClient.syncNihAllowlist("foobar"));
  }
}

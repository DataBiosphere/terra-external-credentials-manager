package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.SamStatusDAO;
import bio.terra.externalcreds.generated.model.Provider;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.model.SystemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

class StatusServiceCacheTest extends BaseTest {

  @Autowired private StatusServiceCache statusServiceCache;

  @MockBean private ProviderOAuthClientCache providerOAuthClientCache;
  @MockBean private SamStatusDAO samStatusDAO;

  @BeforeEach
  void setUp() {
    statusServiceCache.resetProviderStatusCache();
    statusServiceCache.resetSamStatusCache();
  }

  @Test
  void testGetsSuccessfulProviderStatuses() {
    // Arrange
    when(providerOAuthClientCache.getProviderClient(any(Provider.class)))
        .thenReturn(mock(ClientRegistration.class));

    // Act
    var status = statusServiceCache.getProviderStatus(Provider.GITHUB);

    // Assert
    assertTrue(status.isOk());
  }

  @Test
  void testGetsFailureProviderStatuses() {
    // Arrange
    var testMessage = "testFailureMessage";
    when(providerOAuthClientCache.getProviderClient(any(Provider.class)))
        .thenThrow(new IllegalArgumentException(testMessage));

    // Act
    var status = statusServiceCache.getProviderStatus(Provider.GITHUB);

    // Assert
    assertFalse(status.isOk());
    assertEquals(testMessage, status.getMessages().get(0));
  }

  @Test
  void testCachesProviderStatuses() {
    // Arrange
    when(providerOAuthClientCache.getProviderClient(any(Provider.class)))
        .thenReturn(mock(ClientRegistration.class));

    // Act
    var status1 = statusServiceCache.getProviderStatus(Provider.GITHUB);
    var status2 = statusServiceCache.getProviderStatus(Provider.GITHUB);

    // Assert
    assertEquals(status1, status2);
    verify(providerOAuthClientCache, times(1)).getProviderClient(Provider.GITHUB);
  }

  @Test
  void getGetsSuccessfulSamStatus() throws ApiException {
    // Arrange
    var mockStatus = mock(SystemStatus.class);
    when(mockStatus.getOk()).thenReturn(true);
    when(samStatusDAO.getSamStatus()).thenReturn(mockStatus);

    // Act
    var status = statusServiceCache.getSamStatus();

    // Assert
    assertTrue(status.isOk());
  }

  @Test
  void testGetsFailureSamStatus() throws ApiException {
    // Arrange
    var mockStatus = mock(SystemStatus.class);
    when(mockStatus.getOk()).thenReturn(false);
    when(samStatusDAO.getSamStatus()).thenReturn(mockStatus);

    // Act
    var status = statusServiceCache.getSamStatus();

    // Assert
    assertFalse(status.isOk());
  }

  @Test
  void testGetsFailureSamStatusWithMessage() throws ApiException {
    // Arrange
    var testMessage = "testFailureMessage";
    when(samStatusDAO.getSamStatus()).thenThrow(new ApiException(500, testMessage));

    // Act
    var status = statusServiceCache.getSamStatus();

    // Assert
    assertFalse(status.isOk());
    assertTrue(status.getMessages().get(0).contains(testMessage));
  }

  @Test
  void testCachesSamStatus() throws ApiException {
    // Arrange
    var mockStatus = mock(SystemStatus.class);
    when(mockStatus.getOk()).thenReturn(true);
    when(samStatusDAO.getSamStatus()).thenReturn(mockStatus);

    // Act
    var status1 = statusServiceCache.getSamStatus();
    var status2 = statusServiceCache.getSamStatus();

    // Assert
    assertEquals(status1, status2);
    verify(samStatusDAO, times(1)).getSamStatus();
  }
}

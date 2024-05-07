package bio.terra.externalcreds.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import bio.terra.externalcreds.BaseTest;
import bio.terra.externalcreds.dataAccess.StatusDAO;
import bio.terra.externalcreds.generated.model.Provider;
import bio.terra.externalcreds.generated.model.SubsystemStatusDetail;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class StatusServiceTest extends BaseTest {

  @Autowired private StatusService statusService;
  @MockBean private StatusServiceCache statusServiceCache;
  @MockBean private StatusDAO statusDAO;

  @Test
  void testgetSystemStatusDetail() {
    // Arrange
    setupStatuses(true, true, true);

    // Act
    var systemStatus = statusService.getSystemStatusDetail();

    // Assert
    assertTrue(systemStatus.isOk());
  }

  @Test
  void testProviderStatusFailureOk() {
    // Arrange
    setupStatuses(true, true, false);

    // Act
    var systemStatus = statusService.getSystemStatusDetail();

    // Assert
    assertTrue(systemStatus.isOk());
  }

  @Test
  void testSamStatusFailureNotOk() {
    // Arrange
    setupStatuses(true, false, true);

    // Act
    var systemStatus = statusService.getSystemStatusDetail();

    // Assert
    assertFalse(systemStatus.isOk());
  }

  @Test
  void testPostgresStatusFailureNotOk() {
    // Arrange
    setupStatuses(false, true, true);

    // Act
    var systemStatus = statusService.getSystemStatusDetail();

    // Assert
    assertFalse(systemStatus.isOk());
  }

  @Test
  void testSuppliesFailureMessageIfProvided() {
    // Arrange
    setupStatuses(false, true, true);
    when(statusDAO.isPostgresOk()).thenThrow(new RuntimeException("testMessage"));

    // Act
    var systemStatus = statusService.getSystemStatusDetail();

    // Assert
    assertFalse(systemStatus.isOk());
    var postgresStatus =
        systemStatus.getSystems().stream()
            .filter(subsystemStatus -> subsystemStatus.getName().equals("postgres"))
            .findFirst();
    assertPresent(postgresStatus);
    assertEquals("testMessage", postgresStatus.get().getMessages().get(0));
  }

  void setupStatuses(boolean postgresOk, boolean samOk, boolean providerOk) {
    when(statusServiceCache.getProviderStatus(any(Provider.class)))
        .thenReturn(new SubsystemStatusDetail().ok(providerOk));
    when(statusServiceCache.getSamStatus()).thenReturn(new SubsystemStatusDetail().ok(samOk));
    when(statusDAO.isPostgresOk()).thenReturn(postgresOk);
  }
}

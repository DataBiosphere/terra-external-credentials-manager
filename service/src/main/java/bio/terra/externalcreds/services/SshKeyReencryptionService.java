package bio.terra.externalcreds.services;

import bio.terra.common.logging.LoggingUtils;
import bio.terra.externalcreds.config.ExternalCredsConfig;
import bio.terra.externalcreds.dataAccess.SshKeyPairDAO;
import bio.terra.externalcreds.models.SshKeyPairInternal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SshKeyReencryptionService {

  private final ExternalCredsConfig config;
  private final SshKeyPairDAO sshKeyPairDAO;
  private final ScheduledExecutorService scheduler;

  @Autowired
  public SshKeyReencryptionService(ExternalCredsConfig config, SshKeyPairDAO sshKeyPairDAO) {
    this.config = config;
    this.sshKeyPairDAO = sshKeyPairDAO;
    this.scheduler = Executors.newScheduledThreadPool(1);
  }

  @PostConstruct
  public void startStatusChecking() {
    if (config.getKmsConfiguration().isPresent()) {
      scheduler.scheduleAtFixedRate(
          this::reEncryptSshKeyPairSuppressingException,
          config.getKmsConfiguration().get().getInitialDelayDays(),
          config.getKmsConfiguration().get().getReEncryptionDays(),
          TimeUnit.DAYS);
    }
  }

  private void reEncryptSshKeyPairSuppressingException() {
    try {
      reEncryptSshKey();
    } catch (Exception e) {
      LoggingUtils.logAlert(
          log, "Unexpected error during reencryptSshKey execution, see stacktrace below");
      log.warn("Failed to re-encrypt the ssh key with the newest version of the KMS key");
    }
  }

  private void reEncryptSshKey() {
    if (config.getKmsConfiguration().isEmpty()) {
      return;
    }
    log.info("Beginning ssh key re-encryption cronjob");
    List<SshKeyPairInternal> sshKeyPairs =
        sshKeyPairDAO.getSshKeyPairWithExpiredOrNullEncryptionTimeStamp();
    for (var sshKeyPair : sshKeyPairs) {
      sshKeyPairDAO.upsertSshKeyPair(sshKeyPair);
    }
    log.info("Completed re-encrypt ssh key pair using the newest key version");
  }
}

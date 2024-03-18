package bio.terra.externalcreds.exception;

import bio.terra.common.exception.ErrorReportException;

public class DistributedLockException extends ErrorReportException {

  public DistributedLockException(String message) {
    super(message);
  }
}

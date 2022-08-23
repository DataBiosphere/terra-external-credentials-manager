package bio.terra.externalcreds.exception;

import bio.terra.externalcreds.ExternalCredsException;

public class NihCredentialsSyncException extends ExternalCredsException {
  public NihCredentialsSyncException(String message, Throwable cause) {
    super(message, cause);
  }

  public NihCredentialsSyncException(String message) {
    super(message);
  }
}

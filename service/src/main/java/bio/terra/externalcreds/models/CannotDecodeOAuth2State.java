package bio.terra.externalcreds.models;

import bio.terra.externalcreds.ExternalCredsException;

public class CannotDecodeOAuth2State extends ExternalCredsException {
  public CannotDecodeOAuth2State(Throwable cause) {
    super(cause);
  }
}

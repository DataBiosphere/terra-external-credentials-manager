package bio.terra.externalcreds.services;

import bio.terra.common.exception.BadRequestException;

public class InvalidOAuth2State extends BadRequestException {

  public static final String ERROR_MESSAGE =
      "OAuth2 state incorrect, restart authorization sequence.";

  public InvalidOAuth2State() {
    super(ERROR_MESSAGE);
  }

  public InvalidOAuth2State(Throwable cause) {
    super(ERROR_MESSAGE, cause);
  }
}

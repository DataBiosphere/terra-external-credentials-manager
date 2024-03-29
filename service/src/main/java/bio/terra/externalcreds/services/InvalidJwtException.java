package bio.terra.externalcreds.services;

import bio.terra.externalcreds.ExternalCredsException;
import jakarta.annotation.Nullable;
import java.util.List;
import org.springframework.http.HttpStatus;

public class InvalidJwtException extends ExternalCredsException {

  public InvalidJwtException(String message) {
    super(message);
  }

  public InvalidJwtException(String message, Throwable cause) {
    super(message, cause);
  }

  public InvalidJwtException(Throwable cause) {
    super(cause);
  }

  public InvalidJwtException(Throwable cause, HttpStatus statusCode) {
    super(cause, statusCode);
  }

  public InvalidJwtException(
      String message, @Nullable List<String> causes, @Nullable HttpStatus statusCode) {
    super(message, causes, statusCode);
  }

  public InvalidJwtException(
      String message,
      Throwable cause,
      @Nullable List<String> causes,
      @Nullable HttpStatus statusCode) {
    super(message, cause, causes, statusCode);
  }
}

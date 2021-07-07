package bio.terra.externalcreds;

import bio.terra.common.exception.ErrorReportException;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpStatus;

public class ExternalCredsException extends ErrorReportException {

  public ExternalCredsException(String message) {
    super(message);
  }

  public ExternalCredsException(String message, Throwable cause) {
    super(message, cause);
  }

  public ExternalCredsException(Throwable cause) {
    super(cause);
  }

  public ExternalCredsException(Throwable cause, HttpStatus statusCode) {
    super(cause, statusCode);
  }

  public ExternalCredsException(
      String message, @Nullable List<String> causes, @Nullable HttpStatus statusCode) {
    super(message, causes, statusCode);
  }

  public ExternalCredsException(
      String message,
      Throwable cause,
      @Nullable List<String> causes,
      @Nullable HttpStatus statusCode) {
    super(message, cause, causes, statusCode);
  }
}

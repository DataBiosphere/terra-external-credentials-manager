package bio.terra.externalcreds.controllers;

import bio.terra.common.exception.ErrorReportException;
import bio.terra.externalcreds.generated.model.ErrorReport;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

// This module provides a top-level exception handler for controllers.
// All exceptions that rise through the controllers are caught in this handler.
// It converts the exceptions into standard ErrorReport responses.

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
  // -- Error Report - one of our exceptions --
  @ExceptionHandler(ErrorReportException.class)
  public ResponseEntity<ErrorReport> errorReportHandler(ErrorReportException ex) {
    return buildErrorReport(ex, ex.getStatusCode());
  }

  // -- validation exceptions - we don't control the exception raised
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class,
    NoHandlerFoundException.class
  })
  public ResponseEntity<ErrorReport> validationExceptionHandler(Exception ex) {
    log.error("Global exception handler: catch stack", ex);
    // For security reasons, we generally don't want to include the user's invalid (and potentially
    // malicious) input in the error response, which also means we don't include the full exception.
    // Instead, we return a generic error message about input validation.
    var validationErrorMessage =
        "Request could not be parsed or was invalid: "
            + ex.getClass().getSimpleName()
            + ". Ensure that all types are correct and that enums have valid values.";
    var errorReport =
        new ErrorReport()
            .message(validationErrorMessage)
            .statusCode(HttpStatus.BAD_REQUEST.value());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorReport);
  }

  // -- catchall - log so we can understand what we have missed in the handlers above
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorReport> catchallHandler(Exception ex) {
    log.error("Exception caught by catchall handler", ex);
    return buildErrorReport(ex, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private ResponseEntity<ErrorReport> buildErrorReport(
      @NotNull Throwable ex, HttpStatus statusCode) {
    log.error("Global exception handler:", ex);

    var errorReport = new ErrorReport().message(ex.getMessage()).statusCode(statusCode.value());
    return ResponseEntity.status(statusCode).body(errorReport);
  }
}

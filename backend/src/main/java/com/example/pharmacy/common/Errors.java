package com.example.pharmacy.common;

import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class Errors {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ProblemDetail> domain(ApiException e) {
    return problem(e.status, e.getMessage());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException e) {
    return problem(
      409,
      "Conflicting record. Check duplicate supplier invoice, batch details, and field limits."
    );
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HandlerMethodValidationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class,
  })
  ResponseEntity<ProblemDetail> validation(Exception e) {
    return problem(
      400,
      "Invalid input. Check required fields, dates, positive quantities and non-negative prices."
    );
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> forbidden(Exception e) {
    return problem(403, "Owner permission required.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    org.slf4j.LoggerFactory.getLogger(Errors.class).error("Request failed", e);
    return problem(500, "Unexpected server error. Refer to the trace ID.");
  }

  public static ResponseEntity<ProblemDetail> problem(int status, String detail) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    p.setProperty("traceId", MDC.get("traceId"));
    return ResponseEntity.status(status).body(p);
  }
}

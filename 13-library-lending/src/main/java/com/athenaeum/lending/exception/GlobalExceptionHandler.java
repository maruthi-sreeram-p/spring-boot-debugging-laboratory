package com.athenaeum.lending.exception;

import com.athenaeum.lending.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception,
                                                   HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(NoCopyAvailableException.class)
    public ResponseEntity<ApiError> handleNoCopy(NoCopyAvailableException exception,
                                                 HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(CirculationRuleException.class)
    public ResponseEntity<ApiError> handleRule(CirculationRuleException exception,
                                               HttpServletRequest request) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception,
                                                       HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Access is denied", request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception,
                                                     HttpServletRequest request) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Request validation failed", request, details);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleEverythingElse(Exception exception,
                                                         HttpServletRequest request) {
        log.error("Unhandled exception while serving {}", request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", request, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message,
                                           HttpServletRequest request, List<String> details) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}

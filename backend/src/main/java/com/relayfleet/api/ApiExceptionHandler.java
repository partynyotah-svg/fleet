package com.relayfleet.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        Map<String, String> fields = error.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(field -> field.getField(), field -> "Invalid value", (first, ignored) -> first));
        return ResponseEntity.badRequest().body(ApiError.validation(request.getRequestURI(), fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(400, "Invalid request", request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> conflict(DataIntegrityViolationException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, "A record with that identifier already exists", request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    ResponseEntity<ApiError> duplicate(org.springframework.dao.DuplicateKeyException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, "A record with that identifier already exists", request.getRequestURI()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ApiError> stale(ObjectOptimisticLockingFailureException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(409, "This record changed in another session. Refresh and try again.", request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(400, error.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiError> unavailable(IllegalStateException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError.of(503, error.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<ApiError> deviceAuthentication(SecurityException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(401, "Device authentication failed", request.getRequestURI()));
    }
}
package com.brilliantseas.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global Exception Handler for all REST controllers.
 *
 * SECURITY DESIGN:
 * ──────────────────────────────────────────────────────────────────
 *   OWASP A05 — Security Misconfiguration:
 *   - NEVER leak stack traces in production responses
 *   - NEVER leak database SQL state exceptions
 *   - Maintain generic error messages while returning specific HTTP status
 *
 *   Tracing:
 *   - Generates a UUID correlation ID for 500 internal errors
 *   - Logs the full stack trace internally with correlation ID
 *   - Returns ONLY the correlation ID to the user for support reference
 *
 *   Validation:
 *   - Extracts specific field errors for 400 Bad Request
 *   - Safe to return to client (doesn't leak internal state)
 * ──────────────────────────────────────────────────────────────────
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle our custom business logic exceptions.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
        return buildErrorResponse(
                ex.getErrorCode().getHttpStatus(),
                ex.getErrorCode().getCode(),
                ex.getMessage(),
                null
        );
    }

    /**
     * Handle Spring Security authorization failures (@PreAuthorize).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied exception: {}", ex.getMessage());
        return buildErrorResponse(
                403,
                ErrorCode.ACCESS_DENIED.getCode(),
                ErrorCode.ACCESS_DENIED.getMessage(),
                null
        );
    }

    /**
     * Handle Spring Security authentication failures.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication exception: {}", ex.getMessage());
        return buildErrorResponse(
                401,
                ErrorCode.AUTH_INVALID_CREDENTIALS.getCode(),
                "Authentication failed",
                null
        );
    }

    /**
     * Handle Jakarta Bean Validation field errors (@Valid).
     * Safe to expose field names and validation messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        return buildErrorResponse(
                400,
                ErrorCode.VALIDATION_FAILED.getCode(),
                ErrorCode.VALIDATION_FAILED.getMessage(),
                fieldErrors
        );
    }

    /**
     * Handle 404 Not Found mapping.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNotFound(Exception ex) {
        return buildErrorResponse(
                404,
                "SYS_404",
                "Endpoint not found",
                null
        );
    }

    /**
     * Catch-all for unhandled exceptions (Database errors, NPEs, etc.).
     * CRITICAL SECURITY BOUNDARY: Never leak details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex, HttpServletRequest request) {
        // Generate correlation ID to link log to user report
        String correlationId = UUID.randomUUID().toString();
        
        // Log full stack trace internally with correlation ID
        log.error("Unhandled system exception [Correlation ID: {}] URL: {}", 
                correlationId, request.getRequestURL(), ex);

        // Return safe, sanitized message to client
        return buildErrorResponse(
                500,
                ErrorCode.INTERNAL_ERROR.getCode(),
                "An unexpected internal error occurred. Reference: " + correlationId,
                null
        );
    }

    /**
     * Standardize error response envelope.
     * All API errors must follow: { "error": { "code", "message", "details" } }
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            int status, String code, String message, Object details) {
        
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("code", code);
        errorBody.put("message", message);
        
        if (details != null) {
            errorBody.put("details", details);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("error", errorBody);

        return ResponseEntity.status(HttpStatus.valueOf(status)).body(response);
    }
}

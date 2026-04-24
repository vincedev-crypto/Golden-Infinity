package com.brilliantseas.exception;

import lombok.Getter;

/**
 * Enumeration of all application error codes.
 *
 * SECURITY: Error codes are designed to be informative for API consumers
 * without leaking internal implementation details (OWASP A05).
 * Each code maps to an HTTP status and a safe, generic message.
 */
@Getter
public enum ErrorCode {

    // ── Authentication (401) ──────────────────────────────────────
    AUTH_INVALID_CREDENTIALS("AUTH_001", "Invalid email or password", 401),
    AUTH_ACCOUNT_LOCKED("AUTH_002", "Account temporarily locked", 401),
    AUTH_EMAIL_NOT_VERIFIED("AUTH_003", "Email verification required", 401),
    AUTH_TOKEN_EXPIRED("AUTH_004", "Authentication token expired", 401),
    AUTH_TOKEN_INVALID("AUTH_005", "Invalid authentication token", 401),
    AUTH_MFA_REQUIRED("AUTH_006", "Multi-factor authentication required", 401),
    AUTH_MFA_INVALID("AUTH_007", "Invalid MFA code", 401),
    AUTH_REFRESH_TOKEN_INVALID("AUTH_008", "Invalid or expired refresh token", 401),
    AUTH_SESSION_COMPROMISED("AUTH_009", "Session compromised — please re-login", 401),

    // ── Authorization (403) ──────────────────────────────────────
    ACCESS_DENIED("AUTHZ_001", "Insufficient permissions", 403),
    ACCESS_MFA_NOT_VERIFIED("AUTHZ_002", "MFA verification required for this operation", 403),
    ACCESS_RESOURCE_FORBIDDEN("AUTHZ_003", "Access to this resource is not permitted", 403),

    // ── Validation (400) ─────────────────────────────────────────
    VALIDATION_FAILED("VAL_001", "Validation failed", 400),
    INVALID_REQUEST("VAL_002", "Invalid request format", 400),
    DUPLICATE_EMAIL("VAL_003", "Email address already registered", 400),
    PASSWORD_TOO_WEAK("VAL_004", "Password does not meet security requirements", 400),
    PASSWORD_BREACHED("VAL_005", "This password has appeared in data breaches", 400),

    // ── Business Logic (409/422) ─────────────────────────────────
    BOOKING_VOYAGE_FULL("BIZ_001", "Selected voyage and class is fully booked", 409),
    BOOKING_ALREADY_CANCELLED("BIZ_002", "Booking has already been cancelled", 409),
    BOOKING_NOT_FOUND("BIZ_003", "Booking not found", 404),
    VOYAGE_NOT_FOUND("BIZ_004", "Voyage not found", 404),
    VOYAGE_DEPARTED("BIZ_005", "Cannot book on a voyage that has already departed", 422),
    CARGO_NOT_FOUND("BIZ_006", "Cargo booking not found", 404),
    BOL_ALREADY_ISSUED("BIZ_007", "Bill of lading has already been issued and cannot be modified", 409),
    USER_NOT_FOUND("BIZ_008", "User not found", 404),
    FARE_CLASS_NOT_FOUND("BIZ_009", "Fare class not found", 404),

    // ── Rate Limiting (429) ──────────────────────────────────────
    RATE_LIMIT_EXCEEDED("RATE_001", "Too many requests — please try again later", 429),

    // ── Privacy / RA 10173 (various) ─────────────────────────────
    PRIVACY_DELETION_PENDING("PRI_001", "Data deletion request already pending", 409),
    PRIVACY_CONSENT_REQUIRED("PRI_002", "Consent required before proceeding", 422),

    // ── System (500) ─────────────────────────────────────────────
    INTERNAL_ERROR("SYS_001", "Internal error", 500),
    SERVICE_UNAVAILABLE("SYS_002", "Service temporarily unavailable", 503);

    private final String code;
    private final String message;
    private final int httpStatus;

    ErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}

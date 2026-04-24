package com.brilliantseas.audit;

import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.net.InetAddress;
import java.util.UUID;

/**
 * Immutable audit event published via Spring ApplicationEvents.
 *
 * SECURITY DESIGN:
 * ──────────────────────────────────────────────────────────────────
 *   - Published after every mutation and security event
 *   - Consumed by AuditEventListener (async — never blocks main transaction)
 *   - Written to audit_log table via audit_writer DB role (INSERT only)
 *   - PII in old_value/new_value MUST be masked before publishing
 *
 * EVENT TYPES:
 *   Authentication:  USER_REGISTERED, USER_LOGIN_SUCCESS, USER_LOGIN_FAILED,
 *                    USER_LOCKED, USER_UNLOCKED, MFA_ENABLED, MFA_DISABLED,
 *                    REFRESH_TOKEN_ROTATED, SUSPICIOUS_TOKEN_REUSE_DETECTED
 *   Bookings:        BOOKING_CREATED, BOOKING_CONFIRMED, BOOKING_CANCELLED
 *   Cargo:           CARGO_BOOKING_CREATED, BOL_ISSUED, BOL_STATUS_CHANGED
 *   Voyages:         VOYAGE_CREATED, VOYAGE_STATUS_UPDATED
 *   Admin:           ADMIN_ROLE_CHANGED, USER_DEACTIVATED
 *   Privacy (10173): PII_ACCESSED, PII_EXPORT_REQUESTED, PII_DELETE_REQUESTED,
 *                    CONSENT_GRANTED, CONSENT_WITHDRAWN
 *   Security:        PASSWORD_CHANGED, PASSWORD_RESET_REQUESTED
 *
 * OWASP A09 — Security Logging & Monitoring Failures:
 *   Every mutation and security event is captured with full context.
 * ──────────────────────────────────────────────────────────────────
 */
@Getter
public class AuditEvent extends ApplicationEvent {

    private final UUID actorId;
    private final String actorRole;
    private final String actorIp;
    private final String actorAgent;
    private final String action;
    private final String resourceType;
    private final UUID resourceId;
    private final Object oldValue;
    private final Object newValue;
    private final String result;      // SUCCESS, FAILURE, BLOCKED
    private final UUID sessionId;
    private final UUID correlationId;

    @Builder
    public AuditEvent(Object source,
                      UUID actorId,
                      String actorRole,
                      String actorIp,
                      String actorAgent,
                      String action,
                      String resourceType,
                      UUID resourceId,
                      Object oldValue,
                      Object newValue,
                      String result,
                      UUID sessionId,
                      UUID correlationId) {
        super(source);
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.actorIp = actorIp;
        this.actorAgent = actorAgent;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.result = result != null ? result : "SUCCESS";
        this.sessionId = sessionId;
        this.correlationId = correlationId;
    }
}

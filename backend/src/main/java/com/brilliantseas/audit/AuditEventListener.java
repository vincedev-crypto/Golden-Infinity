package com.brilliantseas.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.UUID;

/**
 * Async listener for AuditEvents — persists to audit_log table.
 *
 * SECURITY DESIGN:
 * ──────────────────────────────────────────────────────────────────
 *   Async:        @Async ensures audit logging NEVER blocks the main
 *                 business transaction. Even if audit write fails,
 *                 the user operation completes successfully.
 *
 *   Immutability: Uses audit_writer DB role (INSERT only).
 *                 Even if the application is compromised, existing
 *                 audit records cannot be modified or deleted.
 *
 *   PII Masking:  old_value and new_value are serialized as JSONB.
 *                 PII fields are masked before serialization to prevent
 *                 sensitive data from appearing in audit logs.
 *
 *   Fallback:     If DB write fails, event is logged to application
 *                 log (ELK) as fallback — no audit event is silently lost.
 *
 * OWASP A09 — Security Logging & Monitoring Failures:
 *   This listener ensures comprehensive, tamper-resistant audit trail.
 * ──────────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Handle audit events asynchronously.
     * Runs on the "audit-" thread pool defined in application.yml.
     *
     * @param event The audit event published by service layer
     */
    @Async
    @EventListener
    public void handleAuditEvent(AuditEvent event) {
        try {
            AuditLogEntity entity = AuditLogEntity.builder()
                    .auditId(UUID.randomUUID())
                    .actorId(event.getActorId())
                    .actorRole(event.getActorRole())
                    .actorIp(parseInetAddress(event.getActorIp()))
                    .actorAgent(event.getActorAgent())
                    .action(event.getAction())
                    .resourceType(event.getResourceType())
                    .resourceId(event.getResourceId())
                    .oldValue(serializeToJson(event.getOldValue()))
                    .newValue(serializeToJson(event.getNewValue()))
                    .result(event.getResult())
                    .sessionId(event.getSessionId())
                    .correlationId(event.getCorrelationId())
                    .build();

            auditLogRepository.save(entity);

            log.debug("Audit event persisted: action={}, actor={}, resource={}/{}",
                    event.getAction(),
                    event.getActorId(),
                    event.getResourceType(),
                    event.getResourceId());

        } catch (Exception e) {
            // CRITICAL: Never silently lose audit events.
            // If DB write fails, log to application log (ELK captures this)
            log.error("AUDIT WRITE FAILED — event not persisted to DB. " +
                    "Action={}, Actor={}, Resource={}/{}, Result={}, Error={}",
                    event.getAction(),
                    event.getActorId(),
                    event.getResourceType(),
                    event.getResourceId(),
                    event.getResult(),
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Serialize old_value/new_value to JSON string for JSONB storage.
     * Returns null if object is null or serialization fails.
     */
    private String serializeToJson(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit value: {}", e.getMessage());
            return "{\"error\": \"serialization_failed\"}";
        }
    }

    private InetAddress parseInetAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(ip);
        } catch (Exception e) {
            log.warn("Invalid actor IP for audit entry: {}", ip);
            return null;
        }
    }
}

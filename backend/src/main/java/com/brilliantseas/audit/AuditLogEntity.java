package com.brilliantseas.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for audit_log table.
 * IMMUTABLE: This entity supports INSERT only.
 * The audit_writer DB role has no UPDATE or DELETE permissions.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "event_time", nullable = false, updatable = false)
    @Builder.Default
    private Instant eventTime = Instant.now();

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role", length = 50)
    private String actorRole;

    @Column(name = "actor_ip", columnDefinition = "INET")
    private InetAddress actorIp;

    @Column(name = "actor_agent")
    private String actorAgent;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "old_value", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String newValue;

    @Column(name = "result", nullable = false, length = 20)
    @Builder.Default
    private String result = "SUCCESS";

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "correlation_id")
    private UUID correlationId;
}

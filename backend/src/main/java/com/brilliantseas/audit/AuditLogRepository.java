package com.brilliantseas.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Repository for audit_log — READ operations only from application context.
 * INSERT operations happen via AuditEventListener using audit_writer role.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    @Query("SELECT a FROM AuditLogEntity a WHERE " +
           "(:actorId IS NULL OR a.actorId = :actorId) AND " +
           "(:action IS NULL OR a.action = :action) AND " +
           "(:resourceType IS NULL OR a.resourceType = :resourceType) AND " +
           "(:from IS NULL OR a.eventTime >= :from) AND " +
           "(:to IS NULL OR a.eventTime <= :to) " +
           "ORDER BY a.eventTime DESC")
    Page<AuditLogEntity> searchAuditLogs(
            @Param("actorId") UUID actorId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);

    Page<AuditLogEntity> findByActorIdOrderByEventTimeDesc(UUID actorId, Pageable pageable);

    Page<AuditLogEntity> findByResourceTypeAndResourceIdOrderByEventTimeDesc(
            String resourceType, UUID resourceId, Pageable pageable);
}

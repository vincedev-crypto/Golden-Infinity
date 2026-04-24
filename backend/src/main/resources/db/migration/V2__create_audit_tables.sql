-- ============================================================================
-- V2: Audit & Compliance Tables
-- Brilliant Seas Shipping Corporation
--
-- IMMUTABILITY: audit_log and data_access_log are append-only.
-- The audit_writer DB role (created in V8) has INSERT-only permissions.
-- No application code path may UPDATE or DELETE from these tables.
-- ============================================================================

-- ============================================================================
-- AUDIT LOG (Immutable — INSERT only via audit_writer role)
-- Captures every mutation and security event in the system
-- ============================================================================
CREATE TABLE audit_log (
    audit_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_time      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id        UUID,
    actor_role      VARCHAR(50),
    actor_ip        INET,
    actor_agent     TEXT,
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(100),
    resource_id     UUID,
    old_value       JSONB,
    new_value       JSONB,
    result          VARCHAR(20) NOT NULL DEFAULT 'SUCCESS'
                    CONSTRAINT chk_audit_result CHECK (
                      result IN ('SUCCESS','FAILURE','BLOCKED')
                    ),
    session_id      UUID,
    correlation_id  UUID
);

COMMENT ON TABLE audit_log IS 'IMMUTABLE audit trail — INSERT-only (audit_writer role). Covers OWASP A09:2021';
COMMENT ON COLUMN audit_log.action IS 'Event type: USER_LOGIN_SUCCESS, BOOKING_CREATED, PII_ACCESSED, etc.';
COMMENT ON COLUMN audit_log.old_value IS 'Previous state (JSONB) — PII masked before storage';
COMMENT ON COLUMN audit_log.new_value IS 'New state (JSONB) — PII masked before storage';
COMMENT ON COLUMN audit_log.correlation_id IS 'Request-scoped UUID for distributed tracing';

-- ============================================================================
-- DATA ACCESS LOG (RA 10173 §20 — tracks all PII access)
-- ============================================================================
CREATE TABLE data_access_log (
    access_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    accessed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    accessor_id     UUID,
    data_subject_id UUID,
    data_type       VARCHAR(100) NOT NULL,
    purpose         VARCHAR(200) NOT NULL,
    legal_basis     VARCHAR(100) NOT NULL
                    CONSTRAINT chk_legal_basis CHECK (
                      legal_basis IN ('CONSENT','CONTRACT','LEGAL_OBLIGATION','VITAL_INTEREST','LEGITIMATE_INTEREST')
                    )
);

COMMENT ON TABLE data_access_log IS 'RA 10173 §20 — logs every access to personal information';
COMMENT ON COLUMN data_access_log.data_type IS 'Category: PASSENGER_PII, ID_DOCUMENT, CONTACT_INFO, SEAFARER_CERT';
COMMENT ON COLUMN data_access_log.purpose IS 'Declared purpose: BOOKING_PROCESSING, DATA_PORTABILITY, MANIFEST_GENERATION';
COMMENT ON COLUMN data_access_log.legal_basis IS 'RA 10173 §12 lawful basis for this specific access';

-- ============================================================================
-- V9: Client Appointment Requests
-- Public clients can request appointments; staff/admin manage status.
-- Sensitive phone/contact details use the application PII converter.
-- ============================================================================

CREATE TABLE client_appointments (
    appointment_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_ref      VARCHAR(24) UNIQUE NOT NULL,
    client_name          TEXT NOT NULL,
    client_email         VARCHAR(255) NOT NULL,
    client_phone         BYTEA,
    company_name         TEXT,
    purpose              VARCHAR(60) NOT NULL
                         CONSTRAINT chk_appointment_purpose CHECK (
                           purpose IN ('CREW_MANAGEMENT','VESSEL_OPERATIONS','DOCUMENTATION','ACCOUNTING','GENERAL_INQUIRY')
                         ),
    preferred_start_at   TIMESTAMPTZ NOT NULL,
    preferred_end_at     TIMESTAMPTZ NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'REQUESTED'
                         CONSTRAINT chk_appointment_status CHECK (
                           status IN ('REQUESTED','CONFIRMED','RESCHEDULED','CANCELLED','COMPLETED')
                         ),
    notes                TEXT,
    calendar_invite_uid  VARCHAR(255) UNIQUE NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           UUID REFERENCES users(user_id),
    CONSTRAINT chk_appointment_time_order CHECK (preferred_end_at > preferred_start_at)
);

COMMENT ON TABLE client_appointments IS 'Client appointment requests for Golden Infinity Management Corp.';
COMMENT ON COLUMN client_appointments.client_phone IS 'RA 10173 PII — encrypted by application AES-GCM converter';
COMMENT ON COLUMN client_appointments.appointment_ref IS 'Random reference for client appointment tracking';

CREATE INDEX idx_client_appointments_email ON client_appointments(client_email, created_at DESC);
CREATE INDEX idx_client_appointments_start ON client_appointments(preferred_start_at);
CREATE INDEX idx_client_appointments_status ON client_appointments(status);

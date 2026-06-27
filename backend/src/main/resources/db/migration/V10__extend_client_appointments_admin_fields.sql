ALTER TABLE client_appointments
    ADD COLUMN IF NOT EXISTS internal_notes TEXT,
    ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_client_appointments_status_start
    ON client_appointments(status, preferred_start_at);

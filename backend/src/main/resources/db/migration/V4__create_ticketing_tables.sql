-- ============================================================================
-- V4: Ticketing Tables (PII Domain)
-- Brilliant Seas Shipping Corporation
--
-- SECURITY: passengers.id_number encrypted via pgp_sym_encrypt (pgcrypto)
-- RLS: Enabled in V8 — passengers visible only to booking owner or STAFF+
-- RA 10173: Soft delete on passengers; PII anonymized on erasure request
-- ============================================================================

CREATE TABLE fare_classes (
    fare_class_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_id        UUID NOT NULL REFERENCES routes(route_id),
    class_name      VARCHAR(50) NOT NULL
                    CONSTRAINT chk_class_name CHECK (
                      class_name IN ('ECONOMY','TOURIST','BUSINESS','CABIN','SUITE')
                    ),
    base_fare       NUMERIC(10,2) NOT NULL,
    available_slots INT NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE bookings (
    booking_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_ref     VARCHAR(20) UNIQUE NOT NULL,
    voyage_id       UUID NOT NULL REFERENCES voyages(voyage_id),
    fare_class_id   UUID NOT NULL REFERENCES fare_classes(fare_class_id),
    booked_by       UUID NOT NULL REFERENCES users(user_id),
    booking_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CONSTRAINT chk_booking_status CHECK (
                      booking_status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED','EXPIRED')
                    ),
    total_amount    NUMERIC(10,2) NOT NULL,
    booking_dt      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    payment_status  VARCHAR(20) NOT NULL DEFAULT 'UNPAID'
                    CONSTRAINT chk_payment_status CHECK (
                      payment_status IN ('UNPAID','PENDING','PAID','REFUNDED','FAILED')
                    ),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID REFERENCES users(user_id)
);

COMMENT ON COLUMN bookings.booking_ref IS 'Random alphanumeric reference — never sequential (anti-enumeration)';
COMMENT ON COLUMN bookings.booked_by IS 'RLS ownership column — passengers visible through this FK chain';
COMMENT ON COLUMN bookings.total_amount IS 'Computed server-side from fare_class × passenger count — never client-provided';

CREATE TABLE passengers (
    passenger_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id      UUID NOT NULL REFERENCES bookings(booking_id) ON DELETE CASCADE,
    last_name       TEXT NOT NULL,
    first_name      TEXT NOT NULL,
    middle_name     TEXT,
    birth_date      DATE,
    gender          VARCHAR(10)
                    CONSTRAINT chk_gender CHECK (
                      gender IN ('MALE','FEMALE','OTHER') OR gender IS NULL
                    ),
    id_type         VARCHAR(50),
    id_number       BYTEA,
    nationality     VARCHAR(50) NOT NULL DEFAULT 'Filipino',
    is_senior       BOOLEAN NOT NULL DEFAULT FALSE,
    is_pwd          BOOLEAN NOT NULL DEFAULT FALSE,
    ticket_no       VARCHAR(30) UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

COMMENT ON TABLE passengers IS 'PII table — RLS enabled, id_number pgcrypto encrypted. RA 10173 data subject';
COMMENT ON COLUMN passengers.id_number IS '🔐 pgp_sym_encrypt — government ID number (RA 10173 Sensitive PI)';
COMMENT ON COLUMN passengers.deleted_at IS 'RA 10173 §16(e) erasure — on delete: name→REDACTED, id_number→NULL, birth_date→NULL';

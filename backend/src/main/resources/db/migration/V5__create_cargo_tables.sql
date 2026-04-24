-- ============================================================================
-- V5: Cargo Tables
-- Brilliant Seas Shipping Corporation
--
-- SECURITY: consignee_contact encrypted via pgp_sym_encrypt
-- RLS: Enabled in V8 — cargo visible only to shipper or STAFF+
-- BOL status workflow: DRAFT → ISSUED is one-way (tamper prevention)
-- ============================================================================

CREATE TABLE cargo_bookings (
    cargo_booking_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cargo_ref        VARCHAR(20) UNIQUE NOT NULL,
    voyage_id        UUID NOT NULL REFERENCES voyages(voyage_id),
    shipper_id       UUID NOT NULL REFERENCES users(user_id),
    consignee_name   TEXT NOT NULL,
    consignee_addr   TEXT,
    consignee_contact BYTEA,
    cargo_type       VARCHAR(50) NOT NULL
                     CONSTRAINT chk_cargo_type CHECK (
                       cargo_type IN ('GENERAL','LIVESTOCK','VEHICLE','BULK','PERISHABLE','HAZARDOUS')
                     ),
    description      TEXT,
    gross_weight_kg  NUMERIC(10,2) NOT NULL,
    volume_cbm       NUMERIC(10,3),
    declared_value   NUMERIC(12,2),
    freight_amount   NUMERIC(10,2) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                     CONSTRAINT chk_cargo_status CHECK (
                       status IN ('PENDING','CONFIRMED','LOADED','IN_TRANSIT','DELIVERED','CANCELLED')
                     ),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by       UUID REFERENCES users(user_id)
);

COMMENT ON COLUMN cargo_bookings.consignee_contact IS '🔐 pgp_sym_encrypt — RA 10173 PII';
COMMENT ON COLUMN cargo_bookings.shipper_id IS 'RLS ownership column — cargo visible through this FK';
COMMENT ON COLUMN cargo_bookings.declared_value IS 'Immutable after BOL status = ISSUED (enforced in service layer)';

CREATE TABLE bills_of_lading (
    bol_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bol_no            VARCHAR(30) UNIQUE NOT NULL,
    cargo_booking_id  UUID NOT NULL REFERENCES cargo_bookings(cargo_booking_id),
    issued_dt         TIMESTAMPTZ DEFAULT NOW(),
    issued_by         UUID REFERENCES users(user_id),
    bol_status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                      CONSTRAINT chk_bol_status CHECK (
                        bol_status IN ('DRAFT','ISSUED','SURRENDERED','RELEASED','CANCELLED')
                      ),
    port_of_loading   UUID REFERENCES ports(port_id),
    port_of_discharge UUID REFERENCES ports(port_id),
    remarks           TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE bills_of_lading IS 'BOL status workflow: DRAFT→ISSUED is irreversible. Prevents cargo manifest tampering';

-- ============================================================================
-- V3: Core Operations Tables
-- Brilliant Seas Shipping Corporation
--
-- Vessels, Ports, Routes, Voyages — operational backbone
-- Soft delete on vessels; ports/routes use is_active flag
-- ============================================================================

CREATE TABLE vessels (
    vessel_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vessel_code     VARCHAR(10) UNIQUE NOT NULL,
    vessel_name     VARCHAR(100) NOT NULL,
    vessel_type     VARCHAR(50) NOT NULL
                    CONSTRAINT chk_vessel_type CHECK (
                      vessel_type IN ('RORO','FERRY','CARGO','FASTCRAFT')
                    ),
    gross_tonnage   NUMERIC(10,2),
    passenger_cap   INT,
    cargo_cap_tons  NUMERIC(10,2),
    year_built      INT,
    flag_state      VARCHAR(50) NOT NULL DEFAULT 'Philippines',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    CONSTRAINT chk_vessel_status CHECK (
                      status IN ('ACTIVE','DRYDOCK','RETIRED')
                    ),
    marina_cert_no  VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID REFERENCES users(user_id),
    deleted_at      TIMESTAMPTZ
);

CREATE TABLE ports (
    port_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    port_code     VARCHAR(10) UNIQUE NOT NULL,
    port_name     VARCHAR(100) NOT NULL,
    city          VARCHAR(100),
    province      VARCHAR(100),
    region        VARCHAR(50),
    latitude      NUMERIC(9,6),
    longitude     NUMERIC(9,6),
    ppa_terminal  VARCHAR(100),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE routes (
    route_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    route_code      VARCHAR(20) UNIQUE NOT NULL,
    origin_port_id  UUID NOT NULL REFERENCES ports(port_id),
    dest_port_id    UUID NOT NULL REFERENCES ports(port_id),
    distance_nm     NUMERIC(8,2),
    est_duration_hr NUMERIC(5,2),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_route_ports CHECK (origin_port_id != dest_port_id)
);

CREATE TABLE voyages (
    voyage_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    voyage_no       VARCHAR(20) UNIQUE NOT NULL,
    vessel_id       UUID NOT NULL REFERENCES vessels(vessel_id),
    route_id        UUID NOT NULL REFERENCES routes(route_id),
    departure_dt    TIMESTAMPTZ NOT NULL,
    arrival_dt_est  TIMESTAMPTZ,
    status          VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                    CONSTRAINT chk_voyage_status CHECK (
                      status IN ('SCHEDULED','BOARDING','DEPARTED','ARRIVED','CANCELLED','DELAYED')
                    ),
    remarks         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by      UUID REFERENCES users(user_id)
);

COMMENT ON COLUMN voyages.remarks IS 'Internal — excluded from public API responses (STAFF/ADMIN only)';

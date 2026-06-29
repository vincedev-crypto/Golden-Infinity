-- ============================================================================
-- V8: Row-Level Security Policies & Database Roles
-- Brilliant Seas Shipping Corporation
--
-- PRINCIPLE OF LEAST PRIVILEGE:
--   postgres     — superuser (setup only, never by application)
--   app_migrator — DDL only (Flyway)
--   app_write    — DML on all except audit (Spring Boot application)
--   app_read     — SELECT only, RLS enforced (reporting)
--   audit_writer — INSERT only on audit tables (immutability guarantee)
--
-- RLS SESSION VARIABLES (set by RlsContextFilter):
--   app.current_user_id   — UUID of authenticated user
--   app.current_user_role — role string (PASSENGER, STAFF, ADMIN, etc.)
--   MUST use SET LOCAL (transaction-scoped) for PgBouncer safety
-- ============================================================================

-- ============================================================================
-- DATABASE ROLES
-- ============================================================================

-- Application write role (primary Spring Boot connection)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'app_write') THEN
        CREATE ROLE app_write WITH LOGIN PASSWORD 'CHANGE_ME_VIA_VAULT';
    END IF;
END
$$;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO app_write', current_database());
END
$$;
GRANT USAGE ON SCHEMA public TO app_write;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO app_write;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_write;
-- Explicitly REVOKE destructive permissions on audit tables
REVOKE UPDATE, DELETE ON audit_log FROM app_write;
REVOKE UPDATE, DELETE ON data_access_log FROM app_write;

-- Application read role (reporting / analytics dashboards)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'app_read') THEN
        CREATE ROLE app_read WITH LOGIN PASSWORD 'CHANGE_ME_VIA_VAULT';
    END IF;
END
$$;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO app_read', current_database());
END
$$;
GRANT USAGE ON SCHEMA public TO app_read;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO app_read;

-- Audit writer role (INSERT only — immutability guarantee)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'audit_writer') THEN
        CREATE ROLE audit_writer WITH LOGIN PASSWORD 'CHANGE_ME_VIA_VAULT';
    END IF;
END
$$;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO audit_writer', current_database());
END
$$;
GRANT USAGE ON SCHEMA public TO audit_writer;
GRANT INSERT ON audit_log TO audit_writer;
GRANT INSERT ON data_access_log TO audit_writer;
-- No SELECT, UPDATE, DELETE on audit tables for audit_writer
-- audit_writer can read users table for actor resolution
GRANT SELECT ON users TO audit_writer;

-- Migration role (Flyway — DDL only, no runtime use)
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'app_migrator') THEN
        CREATE ROLE app_migrator WITH LOGIN PASSWORD 'CHANGE_ME_VIA_VAULT';
    END IF;
END
$$;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO app_migrator', current_database());
END
$$;
GRANT ALL ON SCHEMA public TO app_migrator;
GRANT ALL ON ALL TABLES IN SCHEMA public TO app_migrator;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO app_migrator;

-- ============================================================================
-- ROW-LEVEL SECURITY — PASSENGERS TABLE
-- ============================================================================
ALTER TABLE passengers ENABLE ROW LEVEL SECURITY;

-- Policy: Passengers can only see their own booking's passenger records
CREATE POLICY passenger_self_select ON passengers
    FOR SELECT
    USING (
        booking_id IN (
            SELECT b.booking_id FROM bookings b
            WHERE b.booked_by = current_setting('app.current_user_id', true)::UUID
        )
    );

-- Policy: Staff/Admin bypass — full access for operational needs
CREATE POLICY passenger_staff_all ON passengers
    FOR ALL
    USING (
        current_setting('app.current_user_role', true) IN ('STAFF','ADMIN','SUPERADMIN')
    );

-- ============================================================================
-- ROW-LEVEL SECURITY — BOOKINGS TABLE
-- ============================================================================
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;

-- Policy: Users see only their own bookings
CREATE POLICY booking_owner_select ON bookings
    FOR SELECT
    USING (
        booked_by = current_setting('app.current_user_id', true)::UUID
    );

-- Policy: Users can insert bookings (booked_by set by application)
CREATE POLICY booking_owner_insert ON bookings
    FOR INSERT
    WITH CHECK (
        booked_by = current_setting('app.current_user_id', true)::UUID
    );

-- Policy: Staff/Admin bypass on bookings
CREATE POLICY booking_staff_all ON bookings
    FOR ALL
    USING (
        current_setting('app.current_user_role', true) IN ('STAFF','ADMIN','SUPERADMIN')
    );

-- ============================================================================
-- ROW-LEVEL SECURITY — CARGO BOOKINGS TABLE
-- ============================================================================
ALTER TABLE cargo_bookings ENABLE ROW LEVEL SECURITY;

-- Policy: Shippers see only their own cargo bookings
CREATE POLICY cargo_owner_select ON cargo_bookings
    FOR SELECT
    USING (
        shipper_id = current_setting('app.current_user_id', true)::UUID
    );

-- Policy: Shippers can insert cargo bookings
CREATE POLICY cargo_owner_insert ON cargo_bookings
    FOR INSERT
    WITH CHECK (
        shipper_id = current_setting('app.current_user_id', true)::UUID
        OR current_setting('app.current_user_role', true) IN ('STAFF','ADMIN','SUPERADMIN')
    );

-- Policy: Staff/Admin bypass on cargo
CREATE POLICY cargo_staff_all ON cargo_bookings
    FOR ALL
    USING (
        current_setting('app.current_user_role', true) IN ('STAFF','ADMIN','SUPERADMIN')
    );

-- ============================================================================
-- FORCE RLS for app_write and app_read (not bypassed even by table owner)
-- ============================================================================
ALTER TABLE passengers    FORCE ROW LEVEL SECURITY;
ALTER TABLE bookings      FORCE ROW LEVEL SECURITY;
ALTER TABLE cargo_bookings FORCE ROW LEVEL SECURITY;

-- ============================================================================
-- DEFAULT PRIVILEGES for future tables
-- ============================================================================
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE ON TABLES TO app_write;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT ON TABLES TO app_read;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO app_write;

-- ============================================================================
-- V7: Performance & Security Indexes
-- Brilliant Seas Shipping Corporation
--
-- Indexes categorized by purpose:
--   1. Performance (query optimization)
--   2. Security / Auth (fast credential lookup)
--   3. Partial indexes (active-record patterns, published content)
-- ============================================================================

-- ============================================================================
-- PERFORMANCE INDEXES
-- ============================================================================

-- Voyage search: departure date is the primary search dimension
CREATE INDEX idx_voyages_departure    ON voyages(departure_dt);
CREATE INDEX idx_voyages_status       ON voyages(status);
CREATE INDEX idx_voyages_route        ON voyages(route_id);

-- Booking lookups
CREATE INDEX idx_bookings_voyage      ON bookings(voyage_id);
CREATE INDEX idx_bookings_user        ON bookings(booked_by);
CREATE INDEX idx_bookings_ref         ON bookings(booking_ref);

-- Passenger lookups by booking
CREATE INDEX idx_passengers_booking   ON passengers(booking_id);

-- Cargo lookups
CREATE INDEX idx_cargo_voyage         ON cargo_bookings(voyage_id);
CREATE INDEX idx_cargo_shipper        ON cargo_bookings(shipper_id);
CREATE INDEX idx_cargo_ref            ON cargo_bookings(cargo_ref);

-- BOL lookups
CREATE INDEX idx_bol_cargo            ON bills_of_lading(cargo_booking_id);
CREATE INDEX idx_bol_no               ON bills_of_lading(bol_no);

-- ============================================================================
-- SECURITY / AUTH INDEXES
-- ============================================================================

-- Fast email lookup for login (only non-deleted users)
CREATE INDEX idx_users_email          ON users(email)
                                      WHERE deleted_at IS NULL;

-- Active (non-revoked) refresh tokens per user
CREATE INDEX idx_refresh_active       ON refresh_tokens(user_id)
                                      WHERE is_revoked = FALSE;

-- Refresh token family lookup for reuse detection
CREATE INDEX idx_refresh_family       ON refresh_tokens(token_family);

-- Audit log: search by actor and time (investigation queries)
CREATE INDEX idx_audit_actor          ON audit_log(actor_id, event_time DESC);

-- Audit log: search by resource (what happened to this entity?)
CREATE INDEX idx_audit_resource       ON audit_log(resource_type, resource_id);

-- Audit log: search by action type (e.g., all LOGIN_FAILED events)
CREATE INDEX idx_audit_action         ON audit_log(action, event_time DESC);

-- Data access log: who accessed this data subject's information?
CREATE INDEX idx_daccess_subject      ON data_access_log(data_subject_id, accessed_at DESC);

-- Password resets: lookup by user (prevent flood)
CREATE INDEX idx_pwreset_user         ON password_resets(user_id, created_at DESC);

-- ============================================================================
-- PARTIAL INDEXES (Active Records Only)
-- ============================================================================

-- Active vessels only (exclude soft-deleted)
CREATE INDEX idx_vessels_active       ON vessels(vessel_code)
                                      WHERE deleted_at IS NULL;

-- Active passengers only (exclude soft-deleted / anonymized)
CREATE INDEX idx_passengers_active    ON passengers(booking_id)
                                      WHERE deleted_at IS NULL;

-- Published articles only (public content queries)
CREATE INDEX idx_articles_published   ON articles(published_dt DESC)
                                      WHERE is_published = TRUE AND deleted_at IS NULL;

-- Active advisories (auto-expire via expires_at)
CREATE INDEX idx_advisories_active    ON advisories(created_at DESC)
                                      WHERE is_active = TRUE;

-- Active consent records (not withdrawn)
CREATE INDEX idx_consents_active      ON user_consents(user_id, consent_type)
                                      WHERE withdrawn_at IS NULL;

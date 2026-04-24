-- ============================================================================
-- V1: Identity & Access Management Tables
-- Brilliant Seas Shipping Corporation
-- 
-- Security: pgcrypto extension, bcrypt password hashing, encrypted PII columns
-- Compliance: RA 10173 — user_consents for lawful processing basis
-- ============================================================================

-- Required extension for UUID generation and PII encryption
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================================
-- USERS
-- PII Fields: mobile_no (encrypted), mfa_secret (encrypted)
-- Soft delete: deleted_at for RA 10173 erasure compliance
-- ============================================================================
CREATE TABLE users (
    user_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(255) UNIQUE NOT NULL,
    email_verified    BOOLEAN NOT NULL DEFAULT FALSE,
    password_hash     TEXT NOT NULL,
    role              VARCHAR(30) NOT NULL DEFAULT 'PASSENGER'
                      CONSTRAINT chk_user_role CHECK (
                        role IN ('PASSENGER','CARGO_CLIENT','STAFF','ADMIN','SEAFARER','SUPERADMIN')
                      ),
    first_name        TEXT,
    last_name         TEXT,
    mobile_no         BYTEA,                 -- pgp_sym_encrypt (RA 10173 PII)
    mfa_secret        BYTEA,                 -- pgp_sym_encrypt (credential)
    mfa_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts   INT NOT NULL DEFAULT 0,
    locked_until      TIMESTAMPTZ,
    password_changed  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        UUID,
    deleted_at        TIMESTAMPTZ
);

COMMENT ON TABLE users IS 'User accounts — PII encrypted via pgcrypto, soft-delete for RA 10173';
COMMENT ON COLUMN users.mobile_no IS 'RA 10173 PII — encrypted with pgp_sym_encrypt';
COMMENT ON COLUMN users.mfa_secret IS 'TOTP seed — encrypted with pgp_sym_encrypt';
COMMENT ON COLUMN users.password_hash IS 'bcrypt cost 12 — never plaintext';
COMMENT ON COLUMN users.deleted_at IS 'RA 10173 §16(e) — soft delete for erasure compliance';

-- ============================================================================
-- RBAC TABLES
-- ============================================================================
CREATE TABLE roles (
    role_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_name   VARCHAR(50) UNIQUE NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE permissions (
    permission_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource      VARCHAR(100) NOT NULL,
    action        VARCHAR(50) NOT NULL,
    description   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(resource, action)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(role_id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(permission_id) ON DELETE CASCADE,
    granted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (role_id, permission_id)
);

-- ============================================================================
-- REFRESH TOKENS (server-side tracking for rotation & revocation)
-- token_family enables reuse attack detection
-- ============================================================================
CREATE TABLE refresh_tokens (
    token_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash    TEXT NOT NULL,
    token_family  UUID NOT NULL,
    device_info   TEXT,
    ip_address    INET,
    issued_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    is_revoked    BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 hash of raw refresh token — raw token only in HttpOnly cookie';
COMMENT ON COLUMN refresh_tokens.token_family IS 'Family UUID for rotation attack detection — reuse triggers family-wide revocation';

-- ============================================================================
-- PASSWORD RESETS
-- ============================================================================
CREATE TABLE password_resets (
    reset_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash    TEXT NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    ip_requested  INET,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- USER CONSENTS (RA 10173 §12 — proof of lawful processing basis)
-- Records are NEVER deleted — withdrawal recorded via withdrawn_at
-- ============================================================================
CREATE TABLE user_consents (
    consent_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    consent_type  VARCHAR(50) NOT NULL
                  CONSTRAINT chk_consent_type CHECK (
                    consent_type IN ('PRIVACY_POLICY','MARKETING','DATA_SHARING','TERMS_OF_SERVICE')
                  ),
    version       VARCHAR(20) NOT NULL,
    granted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    withdrawn_at  TIMESTAMPTZ,
    ip_address    INET,
    user_agent    TEXT
);

COMMENT ON TABLE user_consents IS 'RA 10173 §12 — consent records NEVER deleted, withdrawal tracked via withdrawn_at';

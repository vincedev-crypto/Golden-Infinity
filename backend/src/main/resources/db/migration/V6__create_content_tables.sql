-- ============================================================================
-- V6: Content Management Tables
-- Brilliant Seas Shipping Corporation
--
-- Articles (news, advisories, regulatory) and system advisories
-- Soft delete on articles; advisories expire via expires_at
-- ============================================================================

CREATE TABLE articles (
    article_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug          VARCHAR(200) UNIQUE NOT NULL,
    title         VARCHAR(300) NOT NULL,
    category      VARCHAR(50) NOT NULL
                  CONSTRAINT chk_article_category CHECK (
                    category IN ('NEWS','ADVISORY','REGULATORY','COMPANY','PROMO')
                  ),
    body          TEXT,
    excerpt       TEXT,
    author_id     UUID REFERENCES users(user_id),
    published_dt  TIMESTAMPTZ,
    is_published  BOOLEAN NOT NULL DEFAULT FALSE,
    is_featured   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);

CREATE TABLE advisories (
    advisory_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message       VARCHAR(500) NOT NULL,
    severity      VARCHAR(20) NOT NULL DEFAULT 'INFO'
                  CONSTRAINT chk_advisory_severity CHECK (
                    severity IN ('INFO','WARNING','CRITICAL')
                  ),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by    UUID REFERENCES users(user_id)
);

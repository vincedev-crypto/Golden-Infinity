# Entity Relationship Diagram with Security Annotations

**Document:** Architecture Document — Section 9  

---

## 9. ERD — Full Schema with Security Annotations

### 9.1 Domain Map

```mermaid
graph TB
    subgraph "Domain 1: IAM 🔐"
        USERS["users"]
        ROLES["roles"]
        PERMS["permissions"]
        RP["role_permissions"]
        RT["refresh_tokens"]
        PR["password_resets"]
        UC["user_consents ⭐NEW"]
    end

    subgraph "Domain 2: Audit 📋"
        AL["audit_log<br/>(IMMUTABLE)"]
        DAL["data_access_log"]
    end

    subgraph "Domain 3: Operations ⚓"
        VESSELS["vessels"]
        PORTS["ports"]
        ROUTES["routes"]
        VOYAGES["voyages"]
    end

    subgraph "Domain 4: Ticketing 🎫"
        FC["fare_classes"]
        BOOKINGS["bookings"]
        PAX["passengers<br/>🔒 RLS + pgcrypto"]
    end

    subgraph "Domain 5: Cargo 📦"
        CB["cargo_bookings<br/>🔒 RLS + pgcrypto"]
        BOL["bills_of_lading"]
    end

    subgraph "Domain 6: Content 📰"
        ART["articles"]
        ADV["advisories"]
    end

    USERS --> ROLES
    ROLES --> RP
    PERMS --> RP
    USERS --> RT
    USERS --> PR
    USERS --> UC

    USERS --> AL
    USERS --> DAL

    USERS --> VESSELS
    PORTS --> ROUTES
    ROUTES --> VOYAGES
    VESSELS --> VOYAGES
    USERS --> VOYAGES

    ROUTES --> FC
    VOYAGES --> BOOKINGS
    FC --> BOOKINGS
    USERS --> BOOKINGS
    BOOKINGS --> PAX

    VOYAGES --> CB
    USERS --> CB
    CB --> BOL
    PORTS --> BOL
    USERS --> BOL

    USERS --> ART
    USERS --> ADV

    style PAX fill:#e74c3c,stroke:#c0392b,color:#fff
    style CB fill:#e74c3c,stroke:#c0392b,color:#fff
    style AL fill:#27ae60,stroke:#219a52,color:#fff
    style DAL fill:#27ae60,stroke:#219a52,color:#fff
    style USERS fill:#f39c12,stroke:#e67e22,color:#fff
    style UC fill:#f39c12,stroke:#e67e22,color:#fff,stroke-dasharray: 5 5
```

**Legend:**  
🔴 Red = PII tables with RLS + pgcrypto encryption  
🟢 Green = Audit tables (immutable, INSERT-only)  
🟡 Yellow = Identity tables (credentials, sensitive)  
⭐ Dashed = New table identified during compliance analysis

---

### 9.2 Detailed ERD — Domain 1: Identity & Access Management

```mermaid
erDiagram
    users {
        UUID user_id PK "gen_random_uuid()"
        VARCHAR email UK "NOT NULL, indexed WHERE deleted_at IS NULL"
        BOOLEAN email_verified "DEFAULT FALSE"
        TEXT password_hash "bcrypt cost 12 🔒"
        VARCHAR role "PASSENGER|CARGO_CLIENT|STAFF|ADMIN|SEAFARER|SUPERADMIN"
        TEXT first_name ""
        TEXT last_name ""
        BYTEA mobile_no "🔐 pgp_sym_encrypt — RA 10173 PII"
        BYTEA mfa_secret "🔐 pgp_sym_encrypt"
        BOOLEAN mfa_enabled "DEFAULT FALSE"
        INT failed_attempts "DEFAULT 0, lockout at 5"
        TIMESTAMPTZ locked_until "exponential backoff"
        TIMESTAMPTZ password_changed "90-day rotation for STAFF/ADMIN"
        BOOLEAN is_active "DEFAULT TRUE"
        TIMESTAMPTZ created_at "DEFAULT NOW()"
        TIMESTAMPTZ updated_at "DEFAULT NOW()"
        UUID created_by "FK nullable"
        TIMESTAMPTZ deleted_at "soft delete — RA 10173 erasure"
    }

    roles {
        UUID role_id PK "gen_random_uuid()"
        VARCHAR role_name UK "NOT NULL"
        TEXT description ""
    }

    permissions {
        UUID permission_id PK "gen_random_uuid()"
        VARCHAR resource "BOOKING|VOYAGE|CARGO|USER|REPORT"
        VARCHAR action "CREATE|READ|UPDATE|DELETE|EXPORT|APPROVE"
        TEXT description ""
    }

    role_permissions {
        UUID role_id FK "CASCADE delete"
        UUID permission_id FK "CASCADE delete"
    }

    refresh_tokens {
        UUID token_id PK "gen_random_uuid()"
        UUID user_id FK "CASCADE delete"
        TEXT token_hash "SHA-256 of raw token 🔒"
        UUID token_family "rotation attack detection"
        TEXT device_info ""
        INET ip_address ""
        TIMESTAMPTZ issued_at "DEFAULT NOW()"
        TIMESTAMPTZ expires_at "NOT NULL, 7 days"
        TIMESTAMPTZ revoked_at ""
        BOOLEAN is_revoked "DEFAULT FALSE"
    }

    password_resets {
        UUID reset_id PK ""
        UUID user_id FK "CASCADE delete"
        TEXT token_hash "SHA-256 🔒"
        TIMESTAMPTZ expires_at "1 hour"
        TIMESTAMPTZ used_at "prevents reuse"
        INET ip_requested ""
    }

    user_consents {
        UUID consent_id PK "gen_random_uuid()"
        UUID user_id FK ""
        VARCHAR consent_type "PRIVACY_POLICY|MARKETING|DATA_SHARING"
        VARCHAR version "consent policy version"
        TIMESTAMPTZ granted_at "NOT NULL"
        TIMESTAMPTZ withdrawn_at "null if active"
        INET ip_address ""
        TEXT user_agent ""
    }

    users ||--o{ refresh_tokens : "has sessions"
    users ||--o{ password_resets : "requests resets"
    users ||--o{ user_consents : "grants consent"
    roles ||--o{ role_permissions : "has"
    permissions ||--o{ role_permissions : "granted via"
```

### 9.3 Detailed ERD — Domain 3-4: Operations & Ticketing

```mermaid
erDiagram
    vessels {
        UUID vessel_id PK ""
        VARCHAR vessel_code UK "e.g. BSS-001"
        VARCHAR vessel_name "NOT NULL"
        VARCHAR vessel_type "RORO|FERRY|CARGO"
        NUMERIC gross_tonnage ""
        INT passenger_cap ""
        NUMERIC cargo_cap_tons ""
        INT year_built ""
        VARCHAR flag_state "DEFAULT Philippines"
        VARCHAR status "ACTIVE|DRYDOCK|RETIRED"
        VARCHAR marina_cert_no ""
        TIMESTAMPTZ deleted_at "soft delete"
    }

    ports {
        UUID port_id PK ""
        VARCHAR port_code UK "MNL|CEB|ILO|DVO"
        VARCHAR port_name "NOT NULL"
        VARCHAR city ""
        VARCHAR province ""
        VARCHAR region ""
        NUMERIC latitude ""
        NUMERIC longitude ""
        VARCHAR ppa_terminal ""
        BOOLEAN is_active "DEFAULT TRUE"
    }

    routes {
        UUID route_id PK ""
        VARCHAR route_code UK "MNL-CEB"
        UUID origin_port_id FK ""
        UUID dest_port_id FK ""
        NUMERIC distance_nm ""
        NUMERIC est_duration_hr ""
        BOOLEAN is_active "DEFAULT TRUE"
    }

    voyages {
        UUID voyage_id PK ""
        VARCHAR voyage_no UK "BSS-2026-0001"
        UUID vessel_id FK ""
        UUID route_id FK ""
        TIMESTAMPTZ departure_dt "NOT NULL, indexed"
        TIMESTAMPTZ arrival_dt_est ""
        VARCHAR status "SCHEDULED|DEPARTED|ARRIVED|CANCELLED|DELAYED"
        TEXT remarks "⚠️ STAFF/ADMIN only in response"
        TIMESTAMPTZ created_at ""
        TIMESTAMPTZ updated_at ""
        UUID created_by FK ""
    }

    fare_classes {
        UUID fare_class_id PK ""
        UUID route_id FK ""
        VARCHAR class_name "ECONOMY|TOURIST|BUSINESS|CABIN"
        NUMERIC base_fare "NOT NULL"
        INT available_slots ""
    }

    bookings {
        UUID booking_id PK ""
        VARCHAR booking_ref UK "🏷️ Random alphanumeric, 10 chars"
        UUID voyage_id FK ""
        UUID fare_class_id FK ""
        UUID booked_by FK "🔒 RLS ownership column"
        VARCHAR booking_status "PENDING|CONFIRMED|CANCELLED|COMPLETED"
        NUMERIC total_amount "computed server-side"
        TIMESTAMPTZ booking_dt ""
        VARCHAR payment_status "UNPAID|PENDING|PAID|REFUNDED"
        TIMESTAMPTZ updated_at ""
    }

    passengers {
        UUID passenger_id PK ""
        UUID booking_id FK "🔒 RLS via bookings.booked_by"
        TEXT last_name "NOT NULL"
        TEXT first_name "NOT NULL"
        TEXT middle_name ""
        DATE birth_date "RA 10173 PII — anonymized on erasure"
        VARCHAR gender ""
        VARCHAR id_type ""
        BYTEA id_number "🔐 pgp_sym_encrypt — SENSITIVE PII"
        VARCHAR nationality "DEFAULT Filipino"
        BOOLEAN is_senior "fare discount flag"
        BOOLEAN is_pwd "fare discount flag"
        VARCHAR ticket_no UK "generated on confirmation"
        TIMESTAMPTZ created_at ""
        TIMESTAMPTZ deleted_at "soft delete — RA 10173"
    }

    ports ||--o{ routes : "origin"
    ports ||--o{ routes : "destination"
    vessels ||--o{ voyages : "operates"
    routes ||--o{ voyages : "on route"
    routes ||--o{ fare_classes : "pricing"
    voyages ||--o{ bookings : "for voyage"
    fare_classes ||--o{ bookings : "at fare"
    bookings ||--o{ passengers : "includes"
```

### 9.4 Detailed ERD — Domain 5: Cargo

```mermaid
erDiagram
    cargo_bookings {
        UUID cargo_booking_id PK ""
        VARCHAR cargo_ref UK "🏷️ Random, 10 chars"
        UUID voyage_id FK ""
        UUID shipper_id FK "🔒 RLS ownership column"
        TEXT consignee_name ""
        TEXT consignee_addr ""
        BYTEA consignee_contact "🔐 pgp_sym_encrypt — PII"
        VARCHAR cargo_type "GENERAL|LIVESTOCK|VEHICLE|BULK"
        TEXT description ""
        NUMERIC gross_weight_kg ""
        NUMERIC volume_cbm ""
        NUMERIC declared_value "🔒 immutable after BOL ISSUED"
        NUMERIC freight_amount "computed server-side"
        VARCHAR status "PENDING|CONFIRMED|LOADED|IN_TRANSIT|DELIVERED"
        TIMESTAMPTZ created_at ""
        TIMESTAMPTZ updated_at ""
        UUID created_by FK ""
    }

    bills_of_lading {
        UUID bol_id PK ""
        VARCHAR bol_no UK "BSS-BOL-2026-0001"
        UUID cargo_booking_id FK ""
        TIMESTAMPTZ issued_dt ""
        UUID issued_by FK "STAFF/ADMIN only"
        VARCHAR bol_status "DRAFT|ISSUED|SURRENDERED|RELEASED"
        UUID port_of_loading FK ""
        UUID port_of_discharge FK ""
        TEXT remarks ""
    }

    cargo_bookings ||--o{ bills_of_lading : "documented by"
```

---

### 9.5 Security Annotation Summary

| Table | RLS Enabled | Encrypted Columns | Audit Events | Soft Delete | RA 10173 Scope |
|-------|:-----------:|:-----------------:|:------------:|:-----------:|:--------------:|
| `users` | ❌ (app-level) | `mobile_no`, `mfa_secret` | LOGIN, REGISTER, LOCK, PASSWORD_CHANGE | ✅ | ✅ Personal Info |
| `passengers` | ✅ | `id_number` | BOOKING_CREATED, PII_ACCESSED | ✅ | ✅ Sensitive PI |
| `bookings` | ✅ | — | BOOKING_CREATED, CANCELLED | ❌ (via passengers) | ✅ (linked to PII) |
| `cargo_bookings` | ✅ | `consignee_contact` | CARGO_BOOKING_CREATED | ❌ | ✅ Personal Info |
| `bills_of_lading` | ❌ (via cargo) | — | BOL_ISSUED | ❌ | ❌ |
| `audit_log` | ❌ | — | N/A (is audit) | ❌ (immutable) | ✅ (contains IPs) |
| `data_access_log` | ❌ | — | N/A (is audit) | ❌ (immutable) | ✅ (RA 10173 §20) |
| `user_consents` | ❌ | — | CONSENT_GRANTED/WITHDRAWN | ❌ (never delete) | ✅ (§12 proof) |
| `refresh_tokens` | ❌ (app-level) | `token_hash` (SHA-256) | TOKEN_ROTATED, REUSE_DETECTED | ❌ (revoked flag) | ✅ (contains IP) |
| `vessels` | ❌ | — | VESSEL_CREATED/UPDATED | ✅ | ❌ |
| `voyages` | ❌ | — | VOYAGE_STATUS_UPDATED | ❌ | ❌ |
| `articles` | ❌ | — | ARTICLE_PUBLISHED | ✅ | ❌ |
| `advisories` | ❌ | — | ADVISORY_CREATED | ❌ | ❌ |

---

### 9.6 Database Role Separation

```mermaid
graph TB
    subgraph "PostgreSQL Roles (Principle of Least Privilege)"
        SU["postgres (superuser)<br/>Schema creation only<br/>Never used by application"]
        MW["app_migrator<br/>Flyway migrations<br/>CREATE, ALTER, DROP on schema<br/>No data access"]
        AW["app_write<br/>Spring Boot application<br/>SELECT, INSERT, UPDATE<br/>on all tables except audit_log"]
        AR["app_read<br/>Reporting/analytics<br/>SELECT only<br/>No PII access (RLS enforced)"]
        AU["audit_writer<br/>Audit subsystem<br/>INSERT only on audit_log + data_access_log<br/>No UPDATE, No DELETE"]
    end

    SU -->|"grants"| MW
    SU -->|"grants"| AW
    SU -->|"grants"| AR
    SU -->|"grants"| AU

    style SU fill:#e74c3c,stroke:#c0392b,color:#fff
    style MW fill:#f39c12,stroke:#e67e22,color:#fff
    style AW fill:#3498db,stroke:#2980b9,color:#fff
    style AR fill:#27ae60,stroke:#219a52,color:#fff
    style AU fill:#8e44ad,stroke:#9b59b6,color:#fff
```

| DB Role | Permissions | Used By | Rationale |
|---------|------------|---------|-----------|
| `postgres` | Superuser | Initial setup only | Never used by application; credentials rotated post-setup |
| `app_migrator` | DDL (CREATE, ALTER, DROP) | Flyway in CI/CD pipeline | Separate from runtime; only active during deployments |
| `app_write` | DML (SELECT, INSERT, UPDATE) on all tables except `audit_log` | Spring Boot application | Primary application role; cannot modify audit trail |
| `app_read` | SELECT on non-PII views | Reporting dashboard, Grafana | Cannot see encrypted columns; RLS strips PII rows |
| `audit_writer` | INSERT only on `audit_log`, `data_access_log` | Audit subsystem (separate connection pool) | Immutability guarantee — even compromised app cannot alter audit history |

---

### 9.7 RLS Policy Details

```sql
-- Policy 1: Passengers can only read their own booking's passenger records
CREATE POLICY passenger_self ON passengers
  FOR SELECT
  USING (booking_id IN (
    SELECT booking_id FROM bookings
    WHERE booked_by = current_setting('app.current_user_id')::UUID
  ));

-- Policy 2: Staff/Admin bypass RLS for operational needs
CREATE POLICY staff_override ON passengers
  FOR ALL
  USING (current_setting('app.current_user_role') IN ('STAFF','ADMIN','SUPERADMIN'));

-- Policy 3: Bookings visible only to owner
CREATE POLICY booking_owner ON bookings
  FOR SELECT
  USING (booked_by = current_setting('app.current_user_id')::UUID);

-- Policy 4: Staff/Admin bypass on bookings
CREATE POLICY booking_staff ON bookings
  FOR ALL
  USING (current_setting('app.current_user_role') IN ('STAFF','ADMIN','SUPERADMIN'));

-- Policy 5: Cargo bookings visible only to shipper
CREATE POLICY cargo_owner ON cargo_bookings
  FOR SELECT
  USING (shipper_id = current_setting('app.current_user_id')::UUID);

-- Policy 6: Staff/Admin bypass on cargo
CREATE POLICY cargo_staff ON cargo_bookings
  FOR ALL
  USING (current_setting('app.current_user_role') IN ('STAFF','ADMIN','SUPERADMIN'));

-- Session variable injection (done by RlsContextFilter via JDBC):
-- SET LOCAL app.current_user_id = '<uuid>';
-- SET LOCAL app.current_user_role = '<role>';
-- SET LOCAL expires at end of transaction (safe with PgBouncer transaction mode)
```

> [!WARNING]
> **PgBouncer Compatibility:** `SET LOCAL` is transaction-scoped and safe with PgBouncer in transaction mode. However, `SET` (without LOCAL) would leak session state between pooled connections. The `RlsContextFilter` MUST use `SET LOCAL` exclusively. This is enforced by code review and integration test.

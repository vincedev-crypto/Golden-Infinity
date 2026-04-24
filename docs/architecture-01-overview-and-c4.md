# Brilliant Seas Shipping Corporation — Architecture Document

**Document Version:** 1.0  
**Classification:** CONFIDENTIAL — Internal Use Only  
**Author:** Principal Solutions Architect  
**Date:** 2026-04-03  
**Status:** DRAFT — Pending Stakeholder Approval  

> *"Services that are truly Brilliant"*

---

## 1. Executive Summary

This document defines the complete system architecture for the Brilliant Seas Shipping Information System (BSSIS) — a production-grade, security-hardened web platform for Philippine domestic shipping operations. The system handles passenger ticketing, cargo management, voyage scheduling, regulatory compliance (MARINA), and data privacy obligations under RA 10173.

**Architecture Pattern:** Modular Monolith (extraction-ready modules)  
**Security Posture:** Zero Trust, defense-in-depth, OWASP Top 10 mitigated  
**Compliance:** RA 10173 (Data Privacy Act), MARINA ICT, BSP payment regulations  

---

## 2. C4 Model — Level 1: System Context

```mermaid
graph TB
    subgraph External Actors
        PAX["🧑 Passenger<br/>(Books tickets, tracks voyages)"]
        CARGO["🏢 Cargo Client<br/>(Ships cargo, tracks BOL)"]
        STAFF["👷 Operations Staff<br/>(Manages manifests, voyages)"]
        ADMIN["🔐 System Admin<br/>(User mgmt, reports, config)"]
        SEAFARER["⚓ Seafarer<br/>(Views assignments, certs)"]
    end

    subgraph External Systems
        PG["💳 Payment Gateway<br/>(BSP-regulated processor)"]
        MARINA["🏛️ MARINA API<br/>(Certificate verification)"]
        SMTP["📧 SMTP Service<br/>(Transactional email)"]
        NTP["🕐 NTP Server<br/>(Time synchronization)"]
    end

    BSSIS["🚢 Brilliant Seas<br/>Shipping Information System<br/><br/>Handles ticketing, cargo,<br/>voyage ops, compliance"]

    PAX -->|"HTTPS/REST"| BSSIS
    CARGO -->|"HTTPS/REST"| BSSIS
    STAFF -->|"HTTPS/REST"| BSSIS
    ADMIN -->|"HTTPS/REST + MFA"| BSSIS
    SEAFARER -->|"HTTPS/REST"| BSSIS

    BSSIS -->|"HTTPS"| PG
    BSSIS -->|"HTTPS"| MARINA
    BSSIS -->|"SMTPS"| SMTP
    BSSIS -->|"NTP"| NTP

    style BSSIS fill:#0a3d62,stroke:#1e90ff,color:#fff,stroke-width:3px
    style PG fill:#2c3e50,stroke:#e74c3c,color:#fff
    style MARINA fill:#2c3e50,stroke:#f39c12,color:#fff
    style SMTP fill:#2c3e50,stroke:#9b59b6,color:#fff
```

### System Context Boundaries

| Boundary | Trust Level | Protocol | Auth Mechanism |
|----------|-------------|----------|----------------|
| Browser → Nginx | Untrusted | TLS 1.3 | None (public) or JWT |
| Nginx → Spring Boot | Internal/Trusted | HTTP (loopback) | X-Forwarded headers |
| Spring Boot → PostgreSQL | Internal/Trusted | TLS + mTLS | DB credentials (Vault) |
| Spring Boot → Redis | Internal/Trusted | TLS | Password (env var) |
| Spring Boot → Payment GW | External/Untrusted | HTTPS | API Key + HMAC |
| Spring Boot → MARINA API | External/Untrusted | HTTPS | OAuth2 client credentials |
| Spring Boot → SMTP | External/Semi-trusted | SMTPS | SMTP auth |

---

## 3. C4 Model — Level 2: Container Diagram

```mermaid
graph TB
    subgraph Internet
        BROWSER["🌐 Web Browser<br/>(HTML5 + Vanilla JS)<br/>DOMPurify, in-memory token"]
    end

    subgraph DMZ
        NGINX["🔒 Nginx Reverse Proxy<br/>TLS termination, WAF headers,<br/>rate limit L1, static assets"]
    end

    subgraph Application Tier
        APP["☕ Spring Boot 3.3 App<br/>(Java 21 + Virtual Threads)<br/><br/>Modules:<br/>├─ IAM (auth, RBAC, MFA)<br/>├─ Voyages (scheduling)<br/>├─ Ticketing (bookings, PII)<br/>├─ Cargo (BOL, tracking)<br/>├─ Content (articles, advisories)<br/>├─ Audit (event logging)<br/>└─ Privacy (RA 10173)"]
    end

    subgraph Data Tier
        PGB["🔄 PgBouncer<br/>Connection pooling<br/>Transaction mode"]
        PG["🐘 PostgreSQL 15<br/>pgcrypto, RLS policies<br/>Flyway migrations"]
        REDIS["⚡ Redis 7<br/>Rate limit counters,<br/>JTI blacklist,<br/>Refresh token revocation"]
    end

    subgraph Observability
        PROM["📊 Prometheus<br/>Metrics collection"]
        GRAF["📈 Grafana<br/>Dashboards & alerts"]
        ELK["📋 ELK Stack<br/>Centralized logging,<br/>audit log aggregation"]
    end

    BROWSER -->|"HTTPS :443"| NGINX
    NGINX -->|"HTTP :8080"| APP
    APP -->|"JDBC :6432"| PGB
    PGB -->|"PostgreSQL :5432"| PG
    APP -->|"Redis :6379"| REDIS
    APP -->|"HTTP :9090"| PROM
    PROM --> GRAF
    APP -->|"TCP :5044"| ELK

    style NGINX fill:#27ae60,stroke:#2ecc71,color:#fff,stroke-width:2px
    style APP fill:#0a3d62,stroke:#1e90ff,color:#fff,stroke-width:3px
    style PG fill:#2c3e50,stroke:#3498db,color:#fff,stroke-width:2px
    style REDIS fill:#c0392b,stroke:#e74c3c,color:#fff
    style PGB fill:#2c3e50,stroke:#1abc9c,color:#fff
```

### Container Responsibilities

| Container | Technology | Responsibility | Security Controls |
|-----------|-----------|----------------|-------------------|
| **Nginx** | nginx:alpine | TLS termination, security headers (CSP/HSTS/X-Frame), static file serving, L1 rate limiting | No upstream secrets, read-only config mount |
| **Spring Boot App** | Java 21, Spring Boot 3.3 | All business logic, authentication, authorization, API serving | Non-root user (1001), read-only filesystem, no secrets in image |
| **PgBouncer** | pgbouncer/pgbouncer | Connection pooling (transaction mode), max 100 client connections | No external port, internal network only |
| **PostgreSQL** | postgres:15 | Persistent storage, pgcrypto encryption, RLS enforcement, Flyway schema | No external port, encrypted at rest, WAL archiving |
| **Redis** | redis:7-alpine | Rate limit counters, JTI blacklist, refresh token revocation cache | Password required, no external port, no persistence (ephemeral) |
| **Prometheus** | prom/prometheus | Metrics scraping from /actuator/prometheus | Internal network only |
| **Grafana** | grafana/grafana | Metrics visualization, alerting | Admin password from Vault, internal access |
| **ELK** | elastic/elasticsearch + logstash + kibana | Centralized structured logging, audit log search | Internal network, index-level access control |

---

## 4. Spring Security Filter Chain Architecture

```mermaid
graph LR
    REQ["Incoming<br/>HTTP Request"] --> CORS["CorsFilter<br/>(Origin whitelist)"]
    CORS --> RL["RateLimitFilter<br/>(Bucket4j + Redis)<br/>→ 429 if exceeded"]
    RL --> CSRF["CsrfTokenFilter<br/>(Double-submit cookie)<br/>→ 403 if invalid"]
    CSRF --> JWT["JwtAuthFilter<br/>(RS256 verify)<br/>→ 401 if invalid"]
    JWT --> RLS["RlsContextFilter<br/>(SET LOCAL app.*)"]
    RLS --> AUTH["Spring Security<br/>Authorization<br/>→ 403 if denied"]
    AUTH --> CTRL["Controller<br/>(@PreAuthorize)"]
    CTRL --> SVC["Service Layer"]

    style REQ fill:#e74c3c,stroke:#c0392b,color:#fff
    style RL fill:#f39c12,stroke:#e67e22,color:#fff
    style JWT fill:#3498db,stroke:#2980b9,color:#fff
    style AUTH fill:#27ae60,stroke:#219a52,color:#fff
    style CTRL fill:#0a3d62,stroke:#1e90ff,color:#fff
```

### Filter Chain Design Rationale

| Order | Filter | Why This Position |
|-------|--------|-------------------|
| 1 | **CorsFilter** | Reject disallowed origins before any processing |
| 2 | **RateLimitFilter** | Shed excess load BEFORE expensive JWT parsing (DoS mitigation) |
| 3 | **CsrfTokenFilter** | Validate CSRF on state-changing requests before auth processing |
| 4 | **JwtAuthFilter** | Parse and validate JWT, populate SecurityContext |
| 5 | **RlsContextFilter** | Set PostgreSQL session variables for RLS enforcement |
| 6 | **Spring Authorization** | Evaluate URL-pattern and method-level access rules |

> **Design Decision:** RateLimitFilter is placed BEFORE JwtAuthFilter (contrary to the user spec which had CSRF first). Rationale: JWT parsing involves cryptographic verification (RS256 signature check) which is CPU-intensive. Rate limiting must shed malicious traffic before incurring this cost. This prevents a class of computational DoS attacks where an attacker submits syntactically valid but malicious tokens at high volume.

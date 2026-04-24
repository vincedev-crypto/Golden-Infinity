package com.brilliantseas.domain;

import com.brilliantseas.security.PiiEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * User Entity.
 * 
 * SECURITY:
 * - passwordHash is never exposed via API
 * - mobileNo and mfaSecret are encrypted at rest
 * - Soft delete via deletedAt for RA 10173 data erasure requests
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID id;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    // SECURITY: mapped but carefully excluded from standard JSON serialization via DTOs
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // PASSENGER, CARGO_CLIENT, STAFF, ADMIN, SEAFARER, SUPERADMIN
    @Column(name = "role", length = 30, nullable = false)
    @Builder.Default
    private String role = "PASSENGER";

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Convert(converter = PiiEncryptionConverter.class)
    @Column(name = "mobile_no", columnDefinition = "BYTEA")
    private String mobileNo;

    @Convert(converter = PiiEncryptionConverter.class)
    @Column(name = "mfa_secret", columnDefinition = "BYTEA")
    private String mfaSecret;

    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private Boolean mfaEnabled = false;

    @Column(name = "failed_attempts", nullable = false)
    @Builder.Default
    private Integer failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "password_changed", nullable = false)
    @Builder.Default
    private Instant passwordChanged = Instant.now();

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

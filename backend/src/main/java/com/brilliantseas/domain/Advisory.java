package com.brilliantseas.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "advisories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class Advisory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "advisory_id")
    private UUID id;

    @Column(name = "message", length = 500, nullable = false)
    private String message;

    // INFO, WARNING, CRITICAL
    @Column(name = "severity", length = 20, nullable = false)
    @Builder.Default
    private String severity = "INFO";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;
}

package com.brilliantseas.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_consents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "consent_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    // PRIVACY_POLICY, MARKETING, DATA_SHARING, TERMS_OF_SERVICE
    @Column(name = "consent_type", length = 50, nullable = false, updatable = false)
    private String consentType;

    @Column(name = "version", length = 20, nullable = false, updatable = false)
    private String version;

    @Column(name = "granted_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant grantedAt = Instant.now();

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "ip_address", columnDefinition = "INET", updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT", updatable = false)
    private String userAgent;

    public void withdraw() {
        this.withdrawnAt = Instant.now();
    }
}

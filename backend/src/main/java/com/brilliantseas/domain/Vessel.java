package com.brilliantseas.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vessels")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vessel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "vessel_id")
    private UUID id;

    @Column(name = "vessel_code", length = 10, unique = true, nullable = false)
    private String vesselCode;

    @Column(name = "vessel_name", length = 100, nullable = false)
    private String vesselName;

    @Column(name = "vessel_type", length = 50, nullable = false)
    private String vesselType;

    @Column(name = "gross_tonnage", precision = 10, scale = 2)
    private BigDecimal grossTonnage;

    @Column(name = "passenger_cap")
    private Integer passengerCap;

    @Column(name = "cargo_cap_tons", precision = 10, scale = 2)
    private BigDecimal cargoCapTons;

    @Column(name = "year_built")
    private Integer yearBuilt;

    @Column(name = "flag_state", length = 50, nullable = false)
    @Builder.Default
    private String flagState = "Philippines";

    // ACTIVE, DRYDOCK, RETIRED
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "marina_cert_no", length = 50)
    private String marinaCertNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

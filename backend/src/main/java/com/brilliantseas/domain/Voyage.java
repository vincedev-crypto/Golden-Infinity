package com.brilliantseas.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voyages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class Voyage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "voyage_id")
    private UUID id;

    @Column(name = "voyage_no", length = 20, unique = true, nullable = false)
    private String voyageNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vessel_id", nullable = false)
    private Vessel vessel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "departure_dt", nullable = false)
    private Instant departureDt;

    @Column(name = "arrival_dt_est")
    private Instant arrivalDtEst;

    // SCHEDULED, BOARDING, DEPARTED, ARRIVED, CANCELLED, DELAYED
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "SCHEDULED";

    // EXCLUDED from public API
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

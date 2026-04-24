package com.brilliantseas.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * Bill of Lading Entity.
 * 
 * SECURITY:
 * - bolStatus changes to ISSUED lock the associated CargoBooking
 */
@Entity
@Table(name = "bills_of_lading")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class BillOfLading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "bol_id")
    private UUID id;

    @Column(name = "bol_no", length = 30, unique = true, nullable = false, updatable = false)
    private String bolNo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cargo_booking_id", nullable = false, updatable = false)
    private CargoBooking cargoBooking;

    @Column(name = "issued_dt", updatable = false)
    @Builder.Default
    private Instant issuedDt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issued_by", updatable = false)
    private User issuedBy;

    // DRAFT, ISSUED, SURRENDERED, RELEASED, CANCELLED
    @Column(name = "bol_status", length = 20, nullable = false)
    @Builder.Default
    private String bolStatus = "DRAFT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_of_loading")
    private Port portOfLoading;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "port_of_discharge")
    private Port portOfDischarge;

    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

package com.brilliantseas.domain;

import com.brilliantseas.security.PiiEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cargo Booking Entity.
 * 
 * SECURITY & COMPLIANCE:
 * - RA 10173 PII: consigneeContact is encrypted at rest using AES-GCM
 * - declaredValue gets frozen (immutable) once BOL is ISSUED
 * - RLS: visible only to shipper_id owner
 */
@Entity
@Table(name = "cargo_bookings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class CargoBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "cargo_booking_id")
    private UUID id;

    @Column(name = "cargo_ref", length = 20, unique = true, nullable = false, updatable = false)
    private String cargoRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voyage_id", nullable = false, updatable = false)
    private Voyage voyage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipper_id", nullable = false, updatable = false)
    private User shipper;

    @Column(name = "consignee_name", nullable = false, columnDefinition = "TEXT")
    private String consigneeName;

    @Column(name = "consignee_addr", columnDefinition = "TEXT")
    private String consigneeAddr;

    // SENSITIVE PII: Encrypted at rest
    @Convert(converter = PiiEncryptionConverter.class)
    @Column(name = "consignee_contact", columnDefinition = "BYTEA")
    private String consigneeContact;

    @Column(name = "cargo_type", length = 50, nullable = false)
    private String cargoType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "gross_weight_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossWeightKg;

    @Column(name = "volume_cbm", precision = 10, scale = 3)
    private BigDecimal volumeCbm;

    @Column(name = "declared_value", precision = 12, scale = 2)
    private BigDecimal declaredValue;

    @Column(name = "freight_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal freightAmount;

    // PENDING, CONFIRMED, LOADED, IN_TRANSIT, DELIVERED, CANCELLED
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

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

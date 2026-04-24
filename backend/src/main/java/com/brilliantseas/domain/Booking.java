package com.brilliantseas.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Booking Entity.
 * 
 * SECURITY:
 * - RLS: postgres database policy ensures rows are only visible to the bookedBy user
 * - totalAmount is computed server-side, never trusted from client
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "booking_id")
    private UUID id;

    @Column(name = "booking_ref", length = 20, unique = true, nullable = false, updatable = false)
    private String bookingRef;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voyage_id", nullable = false, updatable = false)
    private Voyage voyage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fare_class_id", nullable = false, updatable = false)
    private FareClass fareClass;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booked_by", nullable = false, updatable = false)
    private User bookedBy;

    // PENDING, CONFIRMED, CANCELLED, COMPLETED, EXPIRED
    @Column(name = "booking_status", length = 20, nullable = false)
    @Builder.Default
    private String bookingStatus = "PENDING";

    @Column(name = "total_amount", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "booking_dt", nullable = false, updatable = false)
    @Builder.Default
    private Instant bookingDt = Instant.now();

    // UNPAID, PENDING, PAID, REFUNDED, FAILED
    @Column(name = "payment_status", length = 20, nullable = false)
    @Builder.Default
    private String paymentStatus = "UNPAID";

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Passenger> passengers = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    public void addPassenger(Passenger passenger) {
        passengers.add(passenger);
        passenger.setBooking(this);
    }
}

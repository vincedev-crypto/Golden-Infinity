package com.brilliantseas.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fare_classes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareClass {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fare_class_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "class_name", length = 50, nullable = false)
    private String className;

    @Column(name = "base_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(name = "available_slots", nullable = false)
    @Builder.Default
    private Integer availableSlots = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}

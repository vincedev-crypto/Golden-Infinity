package com.brilliantseas.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "route_id")
    private UUID id;

    @Column(name = "route_code", length = 20, unique = true, nullable = false)
    private String routeCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_port_id", nullable = false)
    private Port originPort;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dest_port_id", nullable = false)
    private Port destPort;

    @Column(name = "distance_nm", precision = 8, scale = 2)
    private BigDecimal distanceNm;

    @Column(name = "est_duration_hr", precision = 5, scale = 2)
    private BigDecimal estDurationHr;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

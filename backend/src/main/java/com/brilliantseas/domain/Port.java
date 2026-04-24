package com.brilliantseas.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ports")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Port {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "port_id")
    private UUID id;

    @Column(name = "port_code", length = 10, unique = true, nullable = false)
    private String portCode;

    @Column(name = "port_name", length = 100, nullable = false)
    private String portName;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "region", length = 50)
    private String region;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "ppa_terminal", length = 100)
    private String ppaTerminal;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}

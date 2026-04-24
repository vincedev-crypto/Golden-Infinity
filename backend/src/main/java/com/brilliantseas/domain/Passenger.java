package com.brilliantseas.domain;

import com.brilliantseas.security.PiiEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Passenger Entity.
 * 
 * SECURITY & COMPLIANCE:
 * - RA 10173 PII: Contains sensitive personal information
 * - Encryption: idNumber is encrypted at rest using pgp_sym_encrypt equivalent (AES-GCM) via AttributeConverter
 * - RLS: postgres database policy ensures rows are only visible to the booking owner (unless STAFF/ADMIN)
 * - Soft Delete: deletedAt field for RA 10173 Right to Erasure
 */
@Entity
@Table(name = "passengers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "passenger_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "birth_date")
    private java.time.LocalDate birthDate;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "id_type", length = 50)
    private String idType;

    // SENSITIVE PII: Encrypted at rest
    @Convert(converter = PiiEncryptionConverter.class)
    @Column(name = "id_number", columnDefinition = "BYTEA")
    private String idNumber;

    @Column(name = "nationality", length = 50, nullable = false)
    @Builder.Default
    private String nationality = "Filipino";

    @Column(name = "is_senior", nullable = false)
    @Builder.Default
    private Boolean isSenior = false;

    @Column(name = "is_pwd", nullable = false)
    @Builder.Default
    private Boolean isPwd = false;

    @Column(name = "ticket_no", length = 30, unique = true)
    private String ticketNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    public void anonymizeForErasure() {
        this.lastName = "REDACTED";
        this.firstName = "REDACTED";
        this.middleName = null;
        this.birthDate = null;
        this.idNumber = null;
        this.deletedAt = Instant.now();
    }
}

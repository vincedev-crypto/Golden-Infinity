package com.brilliantseas.domain;

import com.brilliantseas.security.PiiEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "client_appointments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamicUpdate
public class ClientAppointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "appointment_id")
    private UUID id;

    @Column(name = "appointment_ref", length = 24, nullable = false, unique = true, updatable = false)
    private String appointmentRef;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "client_email", nullable = false)
    private String clientEmail;

    @Convert(converter = PiiEncryptionConverter.class)
    @Column(name = "client_phone", columnDefinition = "BYTEA")
    private String clientPhone;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "purpose", length = 60, nullable = false)
    private String purpose;

    @Column(name = "preferred_start_at", nullable = false)
    private Instant preferredStartAt;

    @Column(name = "preferred_end_at", nullable = false)
    private Instant preferredEndAt;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "REQUESTED";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "calendar_invite_uid", nullable = false, unique = true, updatable = false)
    private String calendarInviteUid;

    @Column(name = "status_updated_at")
    private Instant statusUpdatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

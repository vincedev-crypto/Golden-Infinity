package com.brilliantseas.service;

import com.brilliantseas.audit.AuditEvent;
import com.brilliantseas.domain.ClientAppointment;
import com.brilliantseas.exception.BusinessException;
import com.brilliantseas.exception.ErrorCode;
import com.brilliantseas.repository.ClientAppointmentRepository;
import com.brilliantseas.websocket.AppointmentWebSocketPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientAppointmentService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> VALID_PURPOSES = Set.of(
            "CREW_MANAGEMENT",
            "VESSEL_OPERATIONS",
            "DOCUMENTATION",
            "ACCOUNTING",
            "GENERAL_INQUIRY");
    private static final Set<String> VALID_STATUSES = Set.of(
            "REQUESTED",
            "CONFIRMED",
            "RESCHEDULED",
            "CANCELLED",
            "COMPLETED");

    private final ClientAppointmentRepository appointmentRepository;
    private final AppointmentNotificationService notificationService;
    private final AppointmentWebSocketPublisher webSocketPublisher;
    private final ApplicationEventPublisher auditPublisher;

    @Transactional
    public ClientAppointment createAppointment(CreateAppointmentCommand command) {
        String purpose = command.purpose().toUpperCase(Locale.ROOT);
        if (!VALID_PURPOSES.contains(purpose)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported appointment purpose");
        }
        if (!command.preferredEndAt().isAfter(command.preferredStartAt())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Appointment end time must be after start time");
        }
        if (command.preferredStartAt().isBefore(Instant.now().plusSeconds(900))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Appointments must be requested at least 15 minutes in advance");
        }

        ClientAppointment appointment = ClientAppointment.builder()
                .appointmentRef(generateAppointmentRef())
                .clientName(command.clientName().trim())
                .clientEmail(command.clientEmail().trim().toLowerCase(Locale.ROOT))
                .clientPhone(blankToNull(command.clientPhone()))
                .companyName(blankToNull(command.companyName()))
                .purpose(purpose)
                .preferredStartAt(command.preferredStartAt())
                .preferredEndAt(command.preferredEndAt())
                .status("REQUESTED")
                .notes(blankToNull(command.notes()))
                .calendarInviteUid(UUID.randomUUID() + "@golden-infinity.local")
                .build();

        ClientAppointment saved = appointmentRepository.save(appointment);

        auditPublisher.publishEvent(AuditEvent.builder()
                .source(this)
                .action("CLIENT_APPOINTMENT_REQUESTED")
                .resourceType("CLIENT_APPOINTMENT")
                .resourceId(saved.getId())
                .result("SUCCESS")
                .build());

        notificationService.sendAppointmentRequest(saved);
        webSocketPublisher.publish("APPOINTMENT_CREATED", saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ClientAppointment> listAppointments(Instant startAt, Instant endAt, Pageable pageable) {
        return appointmentRepository.findByPreferredStartAtBetweenOrderByPreferredStartAtAsc(startAt, endAt, pageable);
    }

    @Transactional
    public ClientAppointment updateAppointment(UUID appointmentId, UpdateAppointmentCommand command) {
        ClientAppointment appointment = appointmentRepository.findWithLockById(appointmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPOINTMENT_NOT_FOUND));

        String status = appointment.getStatus();
        if (command.status() != null && !command.status().isBlank()) {
            status = command.status().trim().toUpperCase(Locale.ROOT);
            if (!VALID_STATUSES.contains(status)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported appointment status");
            }
        }

        Instant startAt = command.preferredStartAt() != null
                ? command.preferredStartAt()
                : appointment.getPreferredStartAt();
        Instant endAt = command.preferredEndAt() != null
                ? command.preferredEndAt()
                : appointment.getPreferredEndAt();

        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Appointment end time must be after start time");
        }
        if (!status.equals("CANCELLED") && !status.equals("COMPLETED")
                && startAt.isBefore(Instant.now().minusSeconds(60))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Active appointments cannot be scheduled in the past");
        }

        boolean scheduleChanged = !startAt.equals(appointment.getPreferredStartAt())
                || !endAt.equals(appointment.getPreferredEndAt());
        boolean statusChanged = !status.equals(appointment.getStatus());

        appointment.setPreferredStartAt(startAt);
        appointment.setPreferredEndAt(endAt);
        appointment.setStatus(status);
        appointment.setInternalNotes(blankToNull(command.internalNotes()));

        if (scheduleChanged || statusChanged) {
            appointment.setStatusUpdatedAt(Instant.now());
        }

        ClientAppointment saved = appointmentRepository.save(appointment);

        auditPublisher.publishEvent(AuditEvent.builder()
                .source(this)
                .action("CLIENT_APPOINTMENT_UPDATED")
                .resourceType("CLIENT_APPOINTMENT")
                .resourceId(saved.getId())
                .result("SUCCESS")
                .build());

        if (scheduleChanged || statusChanged) {
            notificationService.sendAppointmentUpdate(saved);
        }
        webSocketPublisher.publish("APPOINTMENT_UPDATED", saved);

        return saved;
    }

    private String generateAppointmentRef() {
        for (int attempt = 0; attempt < 10; attempt++) {
            byte[] bytes = new byte[9];
            SECURE_RANDOM.nextBytes(bytes);
            String ref = "APT-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toUpperCase(Locale.ROOT);
            if (!appointmentRepository.existsByAppointmentRef(ref)) {
                return ref;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Could not generate appointment reference");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateAppointmentCommand(
            String clientName,
            String clientEmail,
            String clientPhone,
            String companyName,
            String purpose,
            Instant preferredStartAt,
            Instant preferredEndAt,
            String notes) {
    }

    public record UpdateAppointmentCommand(
            String status,
            Instant preferredStartAt,
            Instant preferredEndAt,
            String internalNotes) {
    }
}

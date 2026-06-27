package com.brilliantseas.websocket;

import com.brilliantseas.domain.ClientAppointment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentWebSocketPublisher {

    private final AppointmentWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public void publish(String eventType, ClientAppointment appointment) {
        AppointmentEvent event = AppointmentEvent.builder()
                .type(eventType)
                .appointment(toPayload(appointment))
                .build();
        try {
            handler.broadcast(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize appointment WebSocket event for {}", appointment.getAppointmentRef(), e);
        }
    }

    private AppointmentPayload toPayload(ClientAppointment appointment) {
        return AppointmentPayload.builder()
                .appointmentId(appointment.getId())
                .appointmentRef(appointment.getAppointmentRef())
                .clientName(appointment.getClientName())
                .clientEmail(appointment.getClientEmail())
                .clientPhone(appointment.getClientPhone())
                .companyName(appointment.getCompanyName())
                .purpose(appointment.getPurpose())
                .preferredStartAt(appointment.getPreferredStartAt())
                .preferredEndAt(appointment.getPreferredEndAt())
                .status(appointment.getStatus())
                .notes(appointment.getNotes())
                .internalNotes(appointment.getInternalNotes())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .statusUpdatedAt(appointment.getStatusUpdatedAt())
                .build();
    }

    @Builder
    public record AppointmentEvent(String type, AppointmentPayload appointment) {
    }

    @Builder
    public record AppointmentPayload(
            UUID appointmentId,
            String appointmentRef,
            String clientName,
            String clientEmail,
            String clientPhone,
            String companyName,
            String purpose,
            Instant preferredStartAt,
            Instant preferredEndAt,
            String status,
            String notes,
            String internalNotes,
            Instant createdAt,
            Instant updatedAt,
            Instant statusUpdatedAt) {
    }
}

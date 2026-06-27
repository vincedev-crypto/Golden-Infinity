package com.brilliantseas.controller;

import com.brilliantseas.domain.ClientAppointment;
import com.brilliantseas.dto.ApiResponse;
import com.brilliantseas.service.ClientAppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class ClientAppointmentController {

    private final ClientAppointmentService appointmentService;

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest request) {
        ClientAppointment appointment = appointmentService.createAppointment(
                new ClientAppointmentService.CreateAppointmentCommand(
                        request.getClientName(),
                        request.getClientEmail(),
                        request.getClientPhone(),
                        request.getCompanyName(),
                        request.getPurpose(),
                        request.getPreferredStartAt(),
                        request.getPreferredEndAt(),
                        request.getNotes()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toResponse(appointment), "Appointment request received."));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','SUPERADMIN')")
    public ResponseEntity<ApiResponse<Page<AppointmentResponse>>> listAppointments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startAt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endAt,
            Pageable pageable) {
        Page<AppointmentResponse> appointments = appointmentService
                .listAppointments(startAt, endAt, pageable)
                .map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.success(appointments));
    }

    @PatchMapping("/{appointmentId}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','SUPERADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateAppointment(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody UpdateAppointmentRequest request) {
        ClientAppointment appointment = appointmentService.updateAppointment(
                appointmentId,
                new ClientAppointmentService.UpdateAppointmentCommand(
                        request.getStatus(),
                        request.getPreferredStartAt(),
                        request.getPreferredEndAt(),
                        request.getInternalNotes()));

        return ResponseEntity.ok(ApiResponse.success(toResponse(appointment), "Appointment updated."));
    }

    private AppointmentResponse toResponse(ClientAppointment appointment) {
        return AppointmentResponse.builder()
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

    @Data
    public static class CreateAppointmentRequest {
        @NotBlank(message = "Client name is required")
        @Size(max = 160, message = "Client name must be 160 characters or fewer")
        private String clientName;

        @NotBlank(message = "Client email is required")
        @Email(message = "Client email must be valid")
        @Size(max = 255, message = "Client email must be 255 characters or fewer")
        private String clientEmail;

        @Size(max = 80, message = "Client phone must be 80 characters or fewer")
        private String clientPhone;

        @Size(max = 180, message = "Company name must be 180 characters or fewer")
        private String companyName;

        @NotBlank(message = "Appointment purpose is required")
        private String purpose;

        @NotNull(message = "Preferred start time is required")
        @Future(message = "Preferred start time must be in the future")
        private Instant preferredStartAt;

        @NotNull(message = "Preferred end time is required")
        @Future(message = "Preferred end time must be in the future")
        private Instant preferredEndAt;

        @Size(max = 2000, message = "Notes must be 2000 characters or fewer")
        private String notes;
    }

    @Data
    public static class UpdateAppointmentRequest {
        private String status;

        @Future(message = "Preferred start time must be in the future")
        private Instant preferredStartAt;

        @Future(message = "Preferred end time must be in the future")
        private Instant preferredEndAt;

        @Size(max = 2000, message = "Internal notes must be 2000 characters or fewer")
        private String internalNotes;
    }

    @Data
    @Builder
    public static class AppointmentResponse {
        private UUID appointmentId;
        private String appointmentRef;
        private String clientName;
        private String clientEmail;
        private String clientPhone;
        private String companyName;
        private String purpose;
        private Instant preferredStartAt;
        private Instant preferredEndAt;
        private String status;
        private String notes;
        private String internalNotes;
        private Instant createdAt;
        private Instant updatedAt;
        private Instant statusUpdatedAt;
    }
}

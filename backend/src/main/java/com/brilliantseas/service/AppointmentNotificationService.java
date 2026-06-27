package com.brilliantseas.service;

import com.brilliantseas.domain.ClientAppointment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentNotificationService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.notifications.appointments.enabled:false}")
    private boolean appointmentNotificationsEnabled;

    @Value("${app.notifications.from:no-reply@golden-infinity.local}")
    private String fromAddress;

    @Value("${app.notifications.staff-email:}")
    private String staffEmail;

    public void sendAppointmentRequest(ClientAppointment appointment) {
        if (!appointmentNotificationsEnabled) {
            log.info("Appointment email notifications disabled; created appointment {} for {}",
                    appointment.getAppointmentRef(), appointment.getClientEmail());
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Appointment notifications enabled but JavaMailSender is unavailable");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(appointment.getClientEmail());
            if (staffEmail != null && !staffEmail.isBlank()) {
                helper.setCc(staffEmail);
            }
            helper.setSubject("Appointment request received: " + appointment.getAppointmentRef());
            helper.setText(buildEmailBody(appointment), false);
            helper.addAttachment(
                    appointment.getAppointmentRef() + ".ics",
                    new ByteArrayResource(buildIcs(appointment).getBytes(StandardCharsets.UTF_8)),
                    "text/calendar");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send appointment notification for {}", appointment.getAppointmentRef(), e);
        }
    }

    public void sendAppointmentUpdate(ClientAppointment appointment) {
        if (!appointmentNotificationsEnabled) {
            log.info("Appointment email notifications disabled; updated appointment {} to {}",
                    appointment.getAppointmentRef(), appointment.getStatus());
            return;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Appointment notifications enabled but JavaMailSender is unavailable");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(appointment.getClientEmail());
            if (staffEmail != null && !staffEmail.isBlank()) {
                helper.setCc(staffEmail);
            }
            helper.setSubject("Appointment " + appointment.getStatus().toLowerCase() + ": "
                    + appointment.getAppointmentRef());
            helper.setText(buildUpdateEmailBody(appointment), false);
            helper.addAttachment(
                    appointment.getAppointmentRef() + ".ics",
                    new ByteArrayResource(buildIcs(appointment).getBytes(StandardCharsets.UTF_8)),
                    "text/calendar");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send appointment update for {}", appointment.getAppointmentRef(), e);
        }
    }

    private String buildEmailBody(ClientAppointment appointment) {
        return """
                Thank you for requesting an appointment with Golden Infinity Management Corp.

                Reference: %s
                Purpose: %s
                Preferred schedule: %s to %s UTC
                Status: %s

                Our team will review the request and contact you to confirm availability.
                """.formatted(
                appointment.getAppointmentRef(),
                appointment.getPurpose(),
                appointment.getPreferredStartAt(),
                appointment.getPreferredEndAt(),
                appointment.getStatus());
    }

    private String buildUpdateEmailBody(ClientAppointment appointment) {
        return """
                Your appointment with Golden Infinity Management Corp. has been updated.

                Reference: %s
                Purpose: %s
                Schedule: %s to %s UTC
                Status: %s

                Please contact our office if you need further assistance.
                """.formatted(
                appointment.getAppointmentRef(),
                appointment.getPurpose(),
                appointment.getPreferredStartAt(),
                appointment.getPreferredEndAt(),
                appointment.getStatus());
    }

    private String buildIcs(ClientAppointment appointment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC);
        String description = escapeIcs("Appointment request " + appointment.getAppointmentRef()
                + " for " + appointment.getPurpose());

        return """
                BEGIN:VCALENDAR
                VERSION:2.0
                PRODID:-//Golden Infinity Management Corp//Client Appointments//EN
                METHOD:REQUEST
                BEGIN:VEVENT
                UID:%s
                DTSTAMP:%s
                DTSTART:%s
                DTEND:%s
                SUMMARY:%s
                DESCRIPTION:%s
                STATUS:TENTATIVE
                END:VEVENT
                END:VCALENDAR
                """.formatted(
                appointment.getCalendarInviteUid(),
                formatter.format(appointment.getCreatedAt()),
                formatter.format(appointment.getPreferredStartAt()),
                formatter.format(appointment.getPreferredEndAt()),
                escapeIcs("Golden Infinity appointment request"),
                description);
    }

    private String escapeIcs(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n");
    }
}

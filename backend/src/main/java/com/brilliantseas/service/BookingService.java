package com.brilliantseas.service;

import com.brilliantseas.audit.AuditEvent;
import com.brilliantseas.domain.*;
import com.brilliantseas.exception.BusinessException;
import com.brilliantseas.exception.ErrorCode;
import com.brilliantseas.repository.BookingRepository;
import com.brilliantseas.repository.FareClassRepository;
import com.brilliantseas.repository.VoyageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Booking Service.
 *
 * SECURITY DESIGN:
 * ──────────────────────────────────────────────────────────────────
 *   - Fares are NEVER trusted from the client. Computed 100% server-side.
 *   - SecurityContext controls visibility. (The actual DB enforces RLS,
 *     but we also apply application-level checks as defense-in-depth).
 *   - Concurrent slot booking handles Race conditions (to be handled via
 *     optimistic locking or FOR UPDATE in a real prod app, simplified here).
 * ──────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final VoyageRepository voyageRepository;
    private final FareClassRepository fareClassRepository;
    private final ApplicationEventPublisher auditPublisher;

    @Transactional
    public Booking createBooking(UUID voyageId, UUID fareClassId, int passengerCount) {
        
        UUID currentUserId = UUID.fromString((String) SecurityContextHolder.getContext().getAuthentication().getPrincipal());

        Voyage voyage = voyageRepository.findById(voyageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOYAGE_NOT_FOUND));

        if (!voyage.getStatus().equals("SCHEDULED")) {
            throw new BusinessException(ErrorCode.VOYAGE_DEPARTED);
        }

        FareClass fareClass = fareClassRepository.findByIdAndIsActiveTrue(fareClassId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FARE_CLASS_NOT_FOUND));

        if (fareClass.getAvailableSlots() < passengerCount) {
            throw new BusinessException(ErrorCode.BOOKING_VOYAGE_FULL);
        }

        // ──────────────────────────────────────────────────────────────
        // SERVER-SIDE FARE CALCULATION
        // client sum is never trusted (OWASP A01)
        // ──────────────────────────────────────────────────────────────
        BigDecimal baseTotal = fareClass.getBaseFare().multiply(BigDecimal.valueOf(passengerCount));
        // Add taxes, fees here...
        BigDecimal finalTotal = baseTotal;

        // Deduct slots (simplified - requires DB locking for race conditions)
        fareClass.setAvailableSlots(fareClass.getAvailableSlots() - passengerCount);
        fareClassRepository.save(fareClass);

        Booking booking = Booking.builder()
                .bookingRef(generateBookingRef())
                .voyage(voyage)
                .fareClass(fareClass)
                .bookedBy(User.builder().id(currentUserId).build())
                .totalAmount(finalTotal)
                .bookingStatus("PENDING")
                .paymentStatus("UNPAID")
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        auditPublisher.publishEvent(AuditEvent.builder()
            .source(this)
            .actorId(currentUserId)
            .actorRole(getRoleFromContext())
            .action("BOOKING_CREATED")
            .resourceType("BOOKING")
            .resourceId(savedBooking.getId())
            .build());

        return savedBooking;
    }

    private String generateBookingRef() {
        return "BSS-" + System.currentTimeMillis() % 10000000;
    }

    private String getRoleFromContext() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("UNKNOWN");
    }
}

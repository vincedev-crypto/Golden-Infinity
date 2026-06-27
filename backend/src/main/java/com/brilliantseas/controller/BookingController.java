package com.brilliantseas.controller;

import com.brilliantseas.domain.Booking;
import com.brilliantseas.dto.ApiResponse;
import com.brilliantseas.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Booking Controller.
 * 
 * SECURITY DESIGN:
 * - Specific authorization via @PreAuthorize
 * - Input validation handled via internal DTO constraints (omitted here for brevity, typically javax.validation)
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PreAuthorize("hasAuthority('BOOKING:CREATE')")
    @PostMapping
    public ResponseEntity<ApiResponse<BookingResponse>> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        
        Booking booking = bookingService.createBooking(
            request.getVoyageId(), 
            request.getFareClassId(), 
            request.getPassengerCount()
        );

        BookingResponse response = BookingResponse.builder()
            .bookingId(booking.getId())
            .bookingRef(booking.getBookingRef())
            .totalAmount(booking.getTotalAmount())
            .status(booking.getBookingStatus())
            .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Booking created successfully."));
    }

    // DTOs for the controller interface
    @Data
    public static class CreateBookingRequest {
        @NotNull(message = "Voyage ID is required")
        private UUID voyageId;

        @NotNull(message = "Fare class ID is required")
        private UUID fareClassId;

        @Min(value = 1, message = "Passenger count must be at least 1")
        @Max(value = 20, message = "Passenger count cannot exceed 20")
        private int passengerCount;
    }

    @Data
    @lombok.Builder
    public static class BookingResponse {
        private UUID bookingId;
        private String bookingRef;
        private BigDecimal totalAmount;
        private String status;
    }
}

package com.brilliantseas.repository;

import com.brilliantseas.domain.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    
    Optional<Booking> findByBookingRef(String bookingRef);

    Page<Booking> findByBookedByIdOrderByBookingDtDesc(UUID userId, Pageable pageable);
    
    // Security note: We use @Query instead of standard method name 
    // to strictly enforce RLS compatibility (this is redundant but defense-in-depth)
    @Query("SELECT b FROM Booking b WHERE b.bookingRef = :bookingRef AND b.bookedBy.id = :userId")
    Optional<Booking> findByBookingRefAndOwner(
        @Param("bookingRef") String bookingRef, 
        @Param("userId") UUID userId
    );
}

package com.brilliantseas.repository;

import com.brilliantseas.domain.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PassengerRepository extends JpaRepository<Passenger, UUID> {
    List<Passenger> findByBookingIdAndDeletedAtIsNull(UUID bookingId);
}

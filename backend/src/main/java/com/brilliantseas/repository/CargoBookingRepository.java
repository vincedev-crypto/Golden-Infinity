package com.brilliantseas.repository;

import com.brilliantseas.domain.CargoBooking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CargoBookingRepository extends JpaRepository<CargoBooking, UUID> {
    Optional<CargoBooking> findByCargoRef(String cargoRef);
    Page<CargoBooking> findByShipperIdOrderByCreatedAtDesc(UUID shipperId, Pageable pageable);
}

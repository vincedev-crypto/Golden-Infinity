package com.brilliantseas.repository;

import com.brilliantseas.domain.Vessel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VesselRepository extends JpaRepository<Vessel, UUID> {
    Optional<Vessel> findByVesselCodeAndDeletedAtIsNull(String vesselCode);
}

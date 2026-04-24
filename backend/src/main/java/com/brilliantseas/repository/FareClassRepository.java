package com.brilliantseas.repository;

import com.brilliantseas.domain.FareClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FareClassRepository extends JpaRepository<FareClass, UUID> {
    List<FareClass> findByRouteIdAndIsActiveTrue(UUID routeId);
    Optional<FareClass> findByIdAndIsActiveTrue(UUID id);
}

package com.brilliantseas.repository;

import com.brilliantseas.domain.Voyage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoyageRepository extends JpaRepository<Voyage, UUID> {
    
    Optional<Voyage> findByVoyageNo(String voyageNo);

    @Query("SELECT v FROM Voyage v WHERE " +
           "(:originPortId IS NULL OR v.route.originPort.id = :originPortId) AND " +
           "(:destPortId IS NULL OR v.route.destPort.id = :destPortId) AND " +
           "(:dateFrom IS NULL OR v.departureDt >= :dateFrom) AND " +
           "(:dateTo IS NULL OR v.departureDt <= :dateTo) AND " +
           "v.status IN ('SCHEDULED', 'BOARDING') " +
           "ORDER BY v.departureDt ASC")
    Page<Voyage> searchAvailableVoyages(
            @Param("originPortId") UUID originPortId,
            @Param("destPortId") UUID destPortId,
            @Param("dateFrom") Instant dateFrom,
            @Param("dateTo") Instant dateTo,
            Pageable pageable);
}

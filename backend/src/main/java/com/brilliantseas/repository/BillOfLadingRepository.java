package com.brilliantseas.repository;

import com.brilliantseas.domain.BillOfLading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillOfLadingRepository extends JpaRepository<BillOfLading, UUID> {
    Optional<BillOfLading> findByBolNo(String bolNo);
}

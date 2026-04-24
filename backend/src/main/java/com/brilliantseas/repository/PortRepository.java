package com.brilliantseas.repository;

import com.brilliantseas.domain.Port;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PortRepository extends JpaRepository<Port, UUID> {
    Optional<Port> findByPortCode(String portCode);
    List<Port> findByIsActiveTrueOrderByPortNameAsc();
}

package com.brilliantseas.repository;

import com.brilliantseas.domain.Advisory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdvisoryRepository extends JpaRepository<Advisory, UUID> {
    
    @Query("SELECT a FROM Advisory a WHERE a.isActive = true AND (a.expiresAt IS NULL OR a.expiresAt > CURRENT_TIMESTAMP) ORDER BY a.createdAt DESC")
    List<Advisory> findActiveAdvisories();
}

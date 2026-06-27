package com.brilliantseas.repository;

import com.brilliantseas.domain.ClientAppointment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientAppointmentRepository extends JpaRepository<ClientAppointment, UUID> {
    boolean existsByAppointmentRef(String appointmentRef);
    Optional<ClientAppointment> findByAppointmentRef(String appointmentRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClientAppointment> findWithLockById(UUID id);

    Page<ClientAppointment> findByPreferredStartAtBetweenOrderByPreferredStartAtAsc(
            Instant startAt,
            Instant endAt,
            Pageable pageable);
}

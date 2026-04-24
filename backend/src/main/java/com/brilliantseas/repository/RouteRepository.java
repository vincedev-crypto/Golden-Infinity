package com.brilliantseas.repository;

import com.brilliantseas.domain.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RouteRepository extends JpaRepository<Route, UUID> {
    Optional<Route> findByRouteCode(String routeCode);
}

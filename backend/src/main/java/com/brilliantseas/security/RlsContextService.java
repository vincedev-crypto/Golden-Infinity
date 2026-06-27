package com.brilliantseas.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class RlsContextService {

    @PersistenceContext
    private EntityManager entityManager;

    public void applyCurrentUserContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }

        String userId = String.valueOf(authentication.getPrincipal());
        String role = authentication.getAuthorities().stream()
                .filter(authority -> authority.getAuthority().startsWith("ROLE_"))
                .findFirst()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .orElse("UNKNOWN");

        entityManager.createNativeQuery("SELECT set_config('app.current_user_id', :userId, true)")
                .setParameter("userId", userId)
                .getSingleResult();
        entityManager.createNativeQuery("SELECT set_config('app.current_user_role', :role, true)")
                .setParameter("role", role)
                .getSingleResult();
    }
}

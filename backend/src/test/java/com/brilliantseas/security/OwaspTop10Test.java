package com.brilliantseas.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ensures baseline OWASP Top 10 security controls remain active.
 * Prevents accidental security regression during refactoring.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev") // use dev just to get it running without full vault
public class OwaspTop10Test {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testA05_SecurityMisconfiguration_HeadersArePresent() throws Exception {
        mockMvc.perform(get("/api/v1/health").secure(true))
            // Check headers provided by Spring Security
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains ; preload"))
            .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()"));
    }

    @Test
    void testA01_BrokenAccessControl_SecureEndpointsAreProtected() throws Exception {
        // Accessing booking endpoint without auth must return 401
        mockMvc.perform(get("/api/v1/bookings"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void testA05_SecurityMisconfiguration_ErrorsReturnJsonNotTrace() throws Exception {
        // Attempting to access a non-existent public endpoint
        mockMvc.perform(get("/api/v1/health/non-existent-path-that-will-404"))
            .andExpect(status().isNotFound())
            // MUST be JSON format
            .andExpect(content().contentType("application/json"))
            // MUST NOT contain a java stack trace
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.error.code").value("SYS_404"));
    }

    @Test
    void testCORS_ConfigurationAppliesProperly() throws Exception {
        // An allowed origin from application.yml
        mockMvc.perform(options("/api/v1/auth/login")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
            .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        // A disallowed origin
        mockMvc.perform(options("/api/v1/auth/login")
                .header("Origin", "http://malicious-site.com")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden());
    }
}

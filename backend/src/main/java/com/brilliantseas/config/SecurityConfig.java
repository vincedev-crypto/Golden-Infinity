package com.brilliantseas.config;

import com.brilliantseas.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 6 configuration — Lambda DSL.
 *
 * SECURITY ARCHITECTURE:
 * ──────────────────────────────────────────────────────────────────
 * Filter Chain Order:
 *   1. CorsFilter         (Spring built-in, configured via bean)
 *   2. RateLimitFilter     (Bucket4j — shed load before JWT parsing)
 *   3. JwtAuthFilter       (RS256 verification, SecurityContext population)
 *   4. Spring Authorization (URL patterns + @PreAuthorize)
 *
 * OWASP COVERAGE:
 *   A01 — Broken Access Control:     URL patterns + @PreAuthorize + RLS
 *   A02 — Cryptographic Failures:    RS256 JWT, bcrypt cost 12
 *   A04 — Insecure Design:           Stateless, defense-in-depth
 *   A05 — Security Misconfiguration: Hardened headers, no defaults
 *   A07 — Auth Failures:             Session policy STATELESS, no cookies for auth state
 *   A08 — Integrity Failures:        CSRF double-submit, SRI on frontend
 * ──────────────────────────────────────────────────────────────────
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${security.cors.allowed-origins}")
    private String allowedOrigins;

    /** Paths accessible without authentication */
    private static final String[] PUBLIC_PATHS = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/password/reset-request",
        "/api/v1/auth/password/reset",
        "/api/v1/auth/refresh",
        "/api/v1/voyages/**",
        "/api/v1/content/**",
        "/api/v1/health",
        "/api/v1/health/ready",
        "/api/v1/csp-report",
        "/actuator/health",
        "/actuator/prometheus",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── CSRF: Disabled (we use stateless JWT + custom CSRF for mutations) ──
            // OWASP A08: Custom double-submit cookie pattern implemented in CsrfTokenFilter
            .csrf(csrf -> csrf.disable())

            // ── CORS: Configured via corsConfigurationSource bean ──
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Session: STATELESS — no server-side session (JWT only) ──
            // OWASP A07: No session fixation risk; no server-side state
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Authorization Rules ──
            // OWASP A01: Explicit path-based access control
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPERADMIN")
                .requestMatchers("/api/v1/staff/**").hasAnyRole("STAFF", "ADMIN", "SUPERADMIN")
                .requestMatchers("/api/v1/privacy/**").authenticated()
                .requestMatchers("/api/v1/bookings/**").authenticated()
                .requestMatchers("/api/v1/cargo/**").authenticated()
                .anyRequest().authenticated()
            )

            // ── JWT Auth Filter: Before UsernamePasswordAuthenticationFilter ──
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // ── Exception Handling: JSON responses only (no redirects) ──
            // OWASP A05: No information leakage in error responses
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write(
                        "{\"error\":{\"code\":\"AUTH_REQUIRED\",\"message\":\"Authentication required\"}}"
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write(
                        "{\"error\":{\"code\":\"ACCESS_DENIED\",\"message\":\"Insufficient permissions\"}}"
                    );
                })
            )

            // ── Security Headers ──
            // OWASP A05: Security Misconfiguration — hardened headers
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())                    // Clickjacking prevention
                .contentTypeOptions(cto -> {})                          // X-Content-Type-Options: nosniff
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .preload(true)
                    .maxAgeInSeconds(31536000))                        // 1 year HSTS
                .referrerPolicy(referrer ->
                    referrer.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions ->
                    permissions.policy("camera=(), microphone=(), geolocation=(), payment=()"))
            );

        return http.build();
    }

    /**
     * BCrypt password encoder with cost factor 12.
     * OWASP A02: Cryptographic Failures — strong adaptive hashing
     * Cost 12 ≈ 250ms per hash — balances security vs. UX
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * CORS configuration — whitelist origins only.
     * OWASP A01: No wildcard origins; only known frontend domains.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
            Arrays.asList(allowedOrigins.split(","))
        );
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
            "Authorization", "Content-Type", "X-CSRF-Token", "X-Correlation-Id"
        ));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);  // Required for HttpOnly cookies
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

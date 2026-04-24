package com.brilliantseas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT Authentication Filter — OncePerRequestFilter.
 *
 * SECURITY DESIGN:
 * ──────────────────────────────────────────────────────────────────
 *   Position:  After RateLimitFilter, before Spring Authorization
 *   Purpose:   Extract JWT from Authorization header, validate RS256
 *              signature, check JTI blacklist (Redis), populate
 *              SecurityContext for downstream authorization.
 *
 *   Token Source:  Authorization: Bearer <token>
 *                  Access token is NEVER read from cookies or localStorage.
 *                  Frontend stores access token in memory-only variable.
 *
 *   JTI Blacklist: When a token is compromised or user logs out,
 *                  the JTI is blacklisted in Redis with TTL matching
 *                  remaining token lifetime. O(1) lookup per request.
 *
 * OWASP COVERAGE:
 *   A01 — Broken Access Control:  SecurityContext populated with exact role/permissions
 *   A02 — Cryptographic Failures: RS256 signature verified; no HS256 accepted
 *   A07 — Auth Failures:          Expired/tampered tokens rejected; JTI blacklist checked
 * ──────────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JTI_BLACKLIST_PREFIX = "jti:blacklist:";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            try {
                // 1. Validate RS256 signature, expiry, issuer
                Claims claims = jwtTokenProvider.validateAccessToken(token);

                // 2. Check JTI blacklist in Redis (O(1) lookup)
                String jti = claims.getId();
                if (jti != null && isJtiBlacklisted(jti)) {
                    log.warn("Blacklisted JTI detected: {} — possible compromised token", jti);
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write(
                        "{\"error\":{\"code\":\"TOKEN_REVOKED\",\"message\":\"Token has been revoked\"}}"
                    );
                    return;
                }

                // 3. Extract identity and authorities from claims
                String userId = claims.getSubject();
                String role = jwtTokenProvider.getRoleFromClaims(claims);
                List<String> permissions = jwtTokenProvider.getPermissionsFromClaims(claims);

                // 4. Build Spring Security authorities
                // Role as ROLE_<name> for hasRole() checks
                // Permissions as <resource>:<action> for hasAuthority() checks
                List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                if (permissions != null) {
                    permissions.stream()
                        .map(SimpleGrantedAuthority::new)
                        .forEach(authorities::add);
                }

                // 5. Create Authentication and set SecurityContext
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

                // Store claims for downstream use (audit, RLS)
                request.setAttribute("jwt.claims", claims);
                request.setAttribute("jwt.userId", userId);
                request.setAttribute("jwt.role", role);

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (ExpiredJwtException e) {
                // Don't log as warning — expired tokens are normal (client will refresh)
                log.debug("Expired access token for subject: {}",
                    e.getClaims() != null ? e.getClaims().getSubject() : "unknown");
                // Clear context and continue — will hit 401 at authorization layer
            } catch (JwtException e) {
                // SECURITY: Signature mismatch, malformed, etc. — potential attack
                log.warn("JWT validation failed from IP {}: {}",
                    getClientIp(request), e.getMessage());
                // Clear context — do NOT populate authentication
            }
        }

        // Continue filter chain — unauthenticated requests will be handled
        // by Spring Security's authorization rules (permitAll or 401)
        filterChain.doFilter(request, response);
    }

    /**
     * Skip JWT filter for paths that definitely don't need it.
     * Optimization: avoids JWT parsing overhead on static assets.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-ui")
            || path.equals("/favicon.ico");
    }

    /**
     * Extract Bearer token from Authorization header.
     * SECURITY: Only accepts Authorization header — never query params or cookies.
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * Check if JTI is blacklisted in Redis.
     * Blacklisted JTIs have TTL matching remaining token lifetime.
     */
    private boolean isJtiBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(
                redisTemplate.hasKey(JTI_BLACKLIST_PREFIX + jti)
            );
        } catch (Exception e) {
            // Redis failure: fail-open would be a security risk.
            // Fail-closed: reject token if we can't verify JTI status.
            log.error("Redis JTI blacklist check failed — failing closed: {}", e.getMessage());
            return true; // SECURITY: fail closed
        }
    }

    /**
     * Extract client IP, respecting X-Forwarded-For from trusted proxy (Nginx).
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

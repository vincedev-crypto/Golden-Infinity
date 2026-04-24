package com.brilliantseas.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

/**
 * JWT Token Provider — RS256 Asymmetric Signing.
 *
 * SECURITY DESIGN:
 * ──────────────────────────────────────────────────────────────────
 *   Algorithm:  RS256 (RSA + SHA-256) — asymmetric
 *   Why RS256:  Private key stays on auth server only.
 *               When extracting to microservices, other services
 *               verify with public key only (no shared secret risk).
 *   No HS256:   HS256 fallback is explicitly rejected to prevent
 *               algorithm confusion attacks.
 *
 * TOKEN DESIGN:
 *   Access Token:  15-minute TTL, contains userId, role, permissions, sessionId
 *   Refresh Token: 7-day TTL, random UUID, stored as SHA-256 hash in DB
 *   JTI:           Every access token has a unique ID for blacklisting
 *
 * OWASP COVERAGE:
 *   A02 — Cryptographic Failures: RS256 asymmetric, no HS256 fallback
 *   A07 — Auth Failures:          Short-lived tokens, rotation, blacklisting
 *   A08 — Integrity Failures:     Signature verification on every request
 * ──────────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${security.jwt.private-key-path}")
    private String privateKeyPath;

    @Value("${security.jwt.public-key-path}")
    private String publicKeyPath;

    @Value("${security.jwt.access-token-expiry-seconds}")
    private long accessTokenExpiry;

    @Value("${security.jwt.refresh-token-expiry-seconds}")
    private long refreshTokenExpiry;

    @Value("${security.jwt.issuer}")
    private String issuer;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            this.privateKey = loadPrivateKey(privateKeyPath);
            this.publicKey = loadPublicKey(publicKeyPath);
            log.info("JWT RS256 key pair loaded successfully");
        } catch (Exception e) {
            log.error("CRITICAL: Failed to load JWT key pair", e);
            throw new IllegalStateException("Cannot start without JWT keys", e);
        }
    }

    /**
     * Generate an access token (RS256 signed).
     *
     * @param userId      User UUID
     * @param role        User role (PASSENGER, STAFF, ADMIN, etc.)
     * @param permissions List of permission strings (e.g., "BOOKING:CREATE")
     * @param sessionId   Session UUID for audit correlation
     * @return Signed JWT string
     */
    public String generateAccessToken(UUID userId, String role,
                                       List<String> permissions, UUID sessionId) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .id(jti)                                          // JTI for blacklisting
                .subject(userId.toString())                       // User ID
                .issuer(issuer)                                   // "brilliant-seas"
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenExpiry)))
                .claim("role", role)
                .claim("permissions", permissions)
                .claim("sessionId", sessionId.toString())
                .signWith(privateKey, Jwts.SIG.RS256)             // RS256 only
                .compact();
    }

    /**
     * Generate a random refresh token (NOT a JWT — opaque token).
     * The raw token is returned to the client in an HttpOnly cookie.
     * Only the SHA-256 hash is stored in the database.
     *
     * @return Random 64-character hex string
     */
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Validate and parse an access token.
     * Rejects: expired, tampered, wrong issuer, HS256 algorithm.
     *
     * @param token Raw JWT string
     * @return Parsed claims
     * @throws JwtException if validation fails
     */
    public Claims validateAccessToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)             // Only RS256 accepted
                    .requireIssuer(issuer)             // Issuer must match
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Access token expired: {}", e.getMessage());
            throw e;
        } catch (SignatureException e) {
            // SECURITY: Possible token tampering or algorithm confusion attack
            log.warn("JWT signature validation failed — potential tampering: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT received: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extract user ID from token without full validation.
     * Used only for logging/correlation when token is already validated.
     */
    public UUID getUserIdFromToken(String token) {
        Claims claims = validateAccessToken(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extract JTI (JWT ID) for blacklist checking.
     */
    public String getJtiFromToken(String token) {
        Claims claims = validateAccessToken(token);
        return claims.getId();
    }

    /**
     * Extract role from validated token claims.
     */
    public String getRoleFromClaims(Claims claims) {
        return claims.get("role", String.class);
    }

    /**
     * Extract permissions from validated token claims.
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromClaims(Claims claims) {
        return claims.get("permissions", List.class);
    }

    /**
     * Get access token expiry in seconds (for cookie maxAge alignment).
     */
    public long getAccessTokenExpirySeconds() {
        return accessTokenExpiry;
    }

    /**
     * Get refresh token expiry in seconds.
     */
    public long getRefreshTokenExpirySeconds() {
        return refreshTokenExpiry;
    }

    // ── Key Loading (PEM format) ────────────────────────────────────────────

    private PrivateKey loadPrivateKey(String path) throws Exception {
        String keyContent = loadKeyContent(path);
        keyContent = keyContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePrivate(spec);
    }

    private PublicKey loadPublicKey(String path) throws Exception {
        String keyContent = loadKeyContent(path);
        keyContent = keyContent
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }

    private String loadKeyContent(String path) throws IOException {
        if (path.startsWith("classpath:")) {
            String resourcePath = path.substring("classpath:".length());
            try (var is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) throw new IOException("Classpath resource not found: " + resourcePath);
                return new String(is.readAllBytes());
            }
        }
        return Files.readString(Path.of(path));
    }
}

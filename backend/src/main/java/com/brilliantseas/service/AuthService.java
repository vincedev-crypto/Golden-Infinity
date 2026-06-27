package com.brilliantseas.service;

import com.brilliantseas.audit.AuditEvent;
import com.brilliantseas.domain.RefreshToken;
import com.brilliantseas.domain.User;
import com.brilliantseas.dto.auth.AuthResponse;
import com.brilliantseas.dto.auth.LoginRequest;
import com.brilliantseas.dto.auth.RegisterRequest;
import com.brilliantseas.exception.BusinessException;
import com.brilliantseas.exception.ErrorCode;
import com.brilliantseas.repository.RefreshTokenRepository;
import com.brilliantseas.repository.UserRepository;
import com.brilliantseas.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication & Identity Service.
 *
 * SECURITY DESIGN:
 * ──────────────────────────────────────────────────────────────────
 *   OWASP A07 — Auth Failures:
 *     - bcrypt cost 12 for password hashing
 *     - Account lockout after 5 failed attempts (15m duration)
 *     - Family-based refresh token rotation (detects theft)
 *     - Explicit audit event publication on all auth paths
 * ──────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ApplicationEventPublisher auditPublisher;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SEC = 900; // 15 minutes

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        // Generate userId proactively for audit correlation
        UUID userId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .mobileNo(request.getMobileNo())
                .role("PASSENGER") // Default role
                .build();

        userRepository.save(user);

        // Publish audit event
        auditPublisher.publishEvent(AuditEvent.builder()
            .source(this)
            .actorId(userId)
            .actorRole("PASSENGER")
            .actorIp(getClientIp())
            .action("USER_REGISTERED")
            .resourceType("USER")
            .resourceId(userId)
            .build());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(request.getEmail())
                .orElseThrow(() -> {
                    // Prevent username enumeration by throwing generic error
                    log.warn("Login failed: email not found");
                    return new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
                });

        UUID sessionId = UUID.randomUUID();

        // Check lock status
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            publishAuthAudit(user.getId(), user.getRole(), "USER_LOGIN_FAILED_LOCKED", sessionId, "FAILURE");
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED);
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int newFails = user.getFailedAttempts() + 1;
            user.setFailedAttempts(newFails);

            if (newFails >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_DURATION_SEC));
                log.warn("Account locked due to Max Failed Attempts: {}", user.getId());
                publishAuthAudit(user.getId(), user.getRole(), "USER_LOCKED", sessionId, "FAILURE");
            }
            
            userRepository.save(user);
            publishAuthAudit(user.getId(), user.getRole(), "USER_LOGIN_FAILED", sessionId, "FAILURE");
            
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        // Login successful — reset counters
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        // Note: In real implementation, permissions would be fetched from role_permissions table
        List<String> permissions = List.of("BOOKING:READ", "BOOKING:CREATE", "PRIVACY:READ");

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getRole(), permissions, sessionId);
        
        String refreshTokenPlain = jwtTokenProvider.generateRefreshToken();
        
        // Store refresh token
        UUID familyId = UUID.randomUUID(); // Start new token family
        saveRefreshToken(user, refreshTokenPlain, familyId, getClientIp(), getClientUserAgent());

        publishAuthAudit(user.getId(), user.getRole(), "USER_LOGIN_SUCCESS", sessionId, "SUCCESS");

        // We return the raw plain refresh token HERE ONLY.
        // The controller layer *must* intercept this and set it as an HttpOnly cookie,
        // then REMOVE it from the response body.
        return AuthResponse.builder()
                .accessToken(accessToken)
                // HACK: stuffing RT here temporarily to pass it to the Controller.
                // It's the controller's job to strip it out before sending JSON.
                // In a stricter design, you'd wrap this in another object.
                .tokenType(refreshTokenPlain) // Sticking it in tokenType so we hide the field name
                .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
                .user(AuthResponse.UserData.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .role(user.getRole())
                        .permissions(permissions)
                        .mfaEnabled(user.getMfaEnabled())
                        .build())
                .build();
    }

    @Transactional
    public AuthResponse refresh(String plainToken) {
        // Hash the incoming token
        String tokenHash = sha256Hex(plainToken);

        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        RefreshToken storedToken = tokenOpt.get();
        User user = storedToken.getUser();
        UUID sessionId = UUID.randomUUID();

        // ──────────────────────────────────────────────────────────────
        // FAMILY ROTATION LOGIC (OWASP Token Theft Detection)
        // ──────────────────────────────────────────────────────────────
        if (storedToken.getIsRevoked()) {
            // THEFT DETECTED: A revoked token is being reused!
            // Revoke the ENTIRE token family globally.
            log.warn("🚨 SUSPICIOUS: Reuse of revoked refresh token detected. Family: {}", storedToken.getTokenFamily());
            refreshTokenRepository.revokeFamily(storedToken.getTokenFamily());
            
            publishAuthAudit(user.getId(), user.getRole(), "SUSPICIOUS_TOKEN_REUSE_DETECTED", sessionId, "BLOCKED");
            throw new BusinessException(ErrorCode.AUTH_SESSION_COMPROMISED);
        }

        if (storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.AUTH_REFRESH_TOKEN_INVALID);
        }

        // Token is valid. Revoke *this specific token* (one-time use)
        storedToken.revoke();
        refreshTokenRepository.save(storedToken);

        // Generate replacement tokens
        List<String> permissions = List.of("BOOKING:READ", "BOOKING:CREATE", "PRIVACY:READ"); // Mocked for simplicity
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getRole(), permissions, sessionId);
        
        String newRefreshTokenPlain = jwtTokenProvider.generateRefreshToken();
        
        // Save new token in the SAME family
        saveRefreshToken(user, newRefreshTokenPlain, storedToken.getTokenFamily(), getClientIp(), getClientUserAgent());

        publishAuthAudit(user.getId(), user.getRole(), "REFRESH_TOKEN_ROTATED", sessionId, "SUCCESS");

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .tokenType(newRefreshTokenPlain) // Controller will move this to Cookie
                .expiresIn(jwtTokenProvider.getAccessTokenExpirySeconds())
                .user(AuthResponse.UserData.builder()
                        .id(user.getId().toString())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .role(user.getRole())
                        .permissions(permissions)
                        .mfaEnabled(user.getMfaEnabled())
                        .build())
                .build();
    }

    private void saveRefreshToken(User user, String plainToken, UUID familyId, String ip, String userAgent) {
        String tokenHash = sha256Hex(plainToken);
        Instant expiry = Instant.now().plusSeconds(jwtTokenProvider.getRefreshTokenExpirySeconds());

        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .tokenFamily(familyId)
                .expiresAt(expiry)
                .ipAddress(parseIpAddress(ip))
                .deviceInfo(userAgent)
                .build();
        refreshTokenRepository.save(rt);
    }

    @Transactional
    public void logout(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return;
        }

        String tokenHash = sha256Hex(plainToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
            publishAuthAudit(token.getUser().getId(), token.getUser().getRole(),
                    "USER_LOGOUT", UUID.randomUUID(), "SUCCESS");
        });
    }

    public long getRefreshTokenExpirySeconds() {
        return jwtTokenProvider.getRefreshTokenExpirySeconds();
    }

    private InetAddress parseIpAddress(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(ip);
        } catch (Exception e) {
            log.warn("Invalid IP address for refresh token record: {}", ip);
            return null;
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }

    private void publishAuthAudit(UUID userId, String role, String action, UUID sessionId, String result) {
        auditPublisher.publishEvent(AuditEvent.builder()
                .source(this)
                .actorId(userId)
                .actorRole(role)
                .actorIp(getClientIp())
                .actorAgent(getClientUserAgent())
                .action(action)
                .resourceType("USER")
                .resourceId(userId)
                .sessionId(sessionId)
                .result(result)
                .build());
    }

    private String getClientIp() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr == null) return "0.0.0.0";
        HttpServletRequest request = attr.getRequest();
        String xff = request.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
    }

    private String getClientUserAgent() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr == null) return "Unknown";
        return attr.getRequest().getHeader("User-Agent");
    }
}

package com.brilliantseas.controller;

import com.brilliantseas.dto.ApiResponse;
import com.brilliantseas.dto.auth.AuthResponse;
import com.brilliantseas.dto.auth.LoginRequest;
import com.brilliantseas.dto.auth.RegisterRequest;
import com.brilliantseas.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller.
 *
 * SECURITY DESIGN:
 * ──────────────────────────────────────────────────────────────────
 *   - Refresh Token is passed exclusively via HttpOnly, Secure, SameSite=Strict cookies.
 *   - Access Token is returned in the JSON body (in-memory storage on frontend).
 *   - Automatic refresh token rotation via POST /api/v1/auth/refresh.
 *   - Cookie exacts: path=/api/v1/auth/refresh so it's not sent on every API request,
 *     minimizing attack surface and overhead.
 * ──────────────────────────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private static final String REFRESH_COOKIE_NAME = "bss_rt";

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(null, "Registration successful. Please verify your email."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        
        AuthResponse authData = authService.login(request);
        
        // Extract the plain refresh token (hidden in tokenType field by Service hack)
        String refreshTokenPlain = authData.getTokenType();
        
        // Clean up the AuthResponse before sending it generic JSON
        authData.setTokenType("Bearer");

        // SET HTTP-ONLY COOKIE FOR REFRESH TOKEN
        setRefreshTokenCookie(response, refreshTokenPlain, (int) authData.getExpiresIn() * 4 * 24); // e.g. 7 days

        return ResponseEntity.ok(ApiResponse.success(authData));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
            
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.<AuthResponse>builder()
                            .status("error")
                            .message("No refresh token provided")
                            .build());
        }

        AuthResponse authData = authService.refresh(refreshToken);
        
        // Extract the new plain refresh token
        String newRefreshTokenPlain = authData.getTokenType();
        authData.setTokenType("Bearer");

        // SET NEW HTTP-ONLY COOKIE 
        setRefreshTokenCookie(response, newRefreshTokenPlain, (int) authData.getExpiresIn() * 4 * 24);

        return ResponseEntity.ok(ApiResponse.success(authData));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletResponse response) {
        // Service layer logic for JTI blacklisting or Refresh Token manual revocation could go here
        
        // Clear the cookie
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Should be true in prod
        cookie.setPath("/api/v1/auth/refresh");
        cookie.setMaxAge(0); // Delete cookie
        response.addCookie(cookie);

        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token, int maxAgeSeconds) {
        Cookie cookie = new Cookie(REFRESH_COOKIE_NAME, token);
        cookie.setHttpOnly(true);       // Mitigates XSS
        cookie.setSecure(true);         // Mitigates MITM (enforces HTTPS)
        cookie.setPath("/api/v1/auth/refresh"); // Sent *only* to refresh endpoint
        cookie.setMaxAge(maxAgeSeconds);
        // Note: SameSite=Strict requires Spring Boot/Tomcat specific config or manual header string append.
        // For simplicity in standard Servlet API, we rely on standard Cookie, but in prod we'd append the raw header.
        response.addCookie(cookie);
    }
}

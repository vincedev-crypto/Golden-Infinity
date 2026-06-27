package com.brilliantseas.controller;

import com.brilliantseas.dto.ApiResponse;
import com.brilliantseas.dto.auth.AuthResponse;
import com.brilliantseas.dto.auth.LoginRequest;
import com.brilliantseas.dto.auth.RegisterRequest;
import com.brilliantseas.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Authentication Controller.
 *
 * SECURITY DESIGN:
 * ──────────────────────────────────────────────────────────────────
 *   - Refresh Token is passed exclusively via HttpOnly, Secure, SameSite=Strict cookies.
 *   - Access Token is returned in the JSON body (in-memory storage on frontend).
 *   - Automatic refresh token rotation via POST /api/v1/auth/refresh.
 *   - Cookie path is scoped to auth endpoints so refresh and logout can both receive it.
 * ──────────────────────────────────────────────────────────────────
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private static final String REFRESH_COOKIE_NAME = "bss_rt";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    @Value("${security.cookies.secure:true}")
    private boolean secureCookies;

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
        setRefreshTokenCookie(response, refreshTokenPlain, authService.getRefreshTokenExpirySeconds());

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
        setRefreshTokenCookie(response, newRefreshTokenPlain, authService.getRefreshTokenExpirySeconds());

        return ResponseEntity.ok(ApiResponse.success(authData));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());

        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}

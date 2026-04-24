package com.brilliantseas.dto.auth;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    // NOTE: refreshToken is purposely NOT included here.
    // It will be sent as an HttpOnly cookie to mitigate XSS.
    
    private String tokenType;
    private long expiresIn;
    
    private UserData user;

    @Data
    @Builder
    public static class UserData {
        private String id;
        private String email;
        private String firstName;
        private String lastName;
        private String role;
        private List<String> permissions;
        private boolean mfaEnabled;
    }
}

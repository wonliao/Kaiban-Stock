package com.kanban.dto.auth;

import com.kanban.domain.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuthResponse {
    
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private Long expiresIn; // seconds
    private UserInfo user;
    
    @Data
    @Builder
    public static class UserInfo {
        private String id;
        private String username;
        private String email;
        private User.UserRole role;
        private LocalDateTime lastLoginAt;
    }
}
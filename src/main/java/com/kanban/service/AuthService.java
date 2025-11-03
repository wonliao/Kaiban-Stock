package com.kanban.service;

import com.kanban.domain.entity.User;
import com.kanban.dto.auth.*;
import com.kanban.repository.UserRepository;
import com.kanban.security.JwtTokenProvider;
import com.kanban.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    
    @Transactional
    public AuthResponse login(LoginRequest loginRequest) {
        // 驗證使用者憑證
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        
        // 生成 tokens
        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal.getId());
        
        // 更新使用者的 refresh token 和最後登入時間
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new RuntimeException("使用者不存在"));
        
        LocalDateTime refreshTokenExpiry = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(System.currentTimeMillis() + tokenProvider.getRefreshTokenExpirationMs()),
                ZoneId.systemDefault()
        );
        
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiresAt(refreshTokenExpiry);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        log.info("使用者登入成功: {}", userPrincipal.getUsername());
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenExpirationMs() / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .lastLoginAt(user.getLastLoginAt())
                        .build())
                .build();
    }
    
    @Transactional
    public AuthResponse register(RegisterRequest registerRequest) {
        // 檢查使用者名稱是否已存在
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            throw new RuntimeException("使用者名稱已存在");
        }
        
        // 檢查電子郵件是否已存在
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException("電子郵件已存在");
        }
        
        // 建立新使用者
        User user = User.builder()
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .passwordHash(passwordEncoder.encode(registerRequest.getPassword()))
                .role(User.UserRole.VIEWER) // 預設角色為檢視者
                .isActive(true)
                .build();
        
        user = userRepository.save(user);
        
        // 自動登入新註冊的使用者
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        registerRequest.getUsername(),
                        registerRequest.getPassword()
                )
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // 生成 tokens
        String accessToken = tokenProvider.generateAccessToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());
        
        // 更新 refresh token
        LocalDateTime refreshTokenExpiry = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(System.currentTimeMillis() + tokenProvider.getRefreshTokenExpirationMs()),
                ZoneId.systemDefault()
        );
        
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiresAt(refreshTokenExpiry);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        log.info("新使用者註冊成功: {}", user.getUsername());
        
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenExpirationMs() / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .lastLoginAt(user.getLastLoginAt())
                        .build())
                .build();
    }
    
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest refreshRequest) {
        String refreshToken = refreshRequest.getRefreshToken();
        
        // 驗證 refresh token
        if (!tokenProvider.validateToken(refreshToken) || !tokenProvider.isRefreshToken(refreshToken)) {
            throw new RuntimeException("無效的 refresh token");
        }
        
        String userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("使用者不存在"));
        
        // 檢查資料庫中的 refresh token 是否匹配且未過期
        if (!refreshToken.equals(user.getRefreshToken()) || 
            user.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Refresh token 已過期或無效");
        }
        
        // 生成新的 access token
        String newAccessToken = tokenProvider.generateAccessToken(
                user.getId(), 
                user.getUsername(), 
                user.getRole()
        );
        
        // 可選：生成新的 refresh token（滾動更新）
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getId());
        LocalDateTime newRefreshTokenExpiry = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(System.currentTimeMillis() + tokenProvider.getRefreshTokenExpirationMs()),
                ZoneId.systemDefault()
        );
        
        user.setRefreshToken(newRefreshToken);
        user.setRefreshTokenExpiresAt(newRefreshTokenExpiry);
        userRepository.save(user);
        
        log.info("Token 刷新成功: {}", user.getUsername());
        
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessTokenExpirationMs() / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .role(user.getRole())
                        .lastLoginAt(user.getLastLoginAt())
                        .build())
                .build();
    }
    
    @Transactional
    public void logout(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("使用者不存在"));
        
        // 清除 refresh token
        user.setRefreshToken(null);
        user.setRefreshTokenExpiresAt(null);
        userRepository.save(user);
        
        // 清除 Security Context
        SecurityContextHolder.clearContext();
        
        log.info("使用者登出成功: {}", user.getUsername());
    }
}
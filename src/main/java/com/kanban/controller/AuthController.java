package com.kanban.controller;

import com.kanban.dto.SuccessResponse;
import com.kanban.dto.auth.*;
import com.kanban.security.UserPrincipal;
import com.kanban.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<SuccessResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("登入請求: {}", loginRequest.getUsername());
        AuthResponse response = authService.login(loginRequest);

        // 轉換為前端期望的格式
        Map<String, Object> data = new HashMap<>();
        data.put("user", response.getUser());
        data.put("token", response.getAccessToken());  // 前端期望 "token" 而不是 "accessToken"
        data.put("refreshToken", response.getRefreshToken());
        data.put("tokenType", response.getTokenType());
        data.put("expiresIn", response.getExpiresIn());

        return ResponseEntity.ok(SuccessResponse.of("登入成功", data));
    }
    
    @PostMapping("/register")
    public ResponseEntity<SuccessResponse> register(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("註冊請求: {}", registerRequest.getUsername());
        AuthResponse response = authService.register(registerRequest);

        // 轉換為前端期望的格式
        Map<String, Object> data = new HashMap<>();
        data.put("user", response.getUser());
        data.put("token", response.getAccessToken());
        data.put("refreshToken", response.getRefreshToken());
        data.put("tokenType", response.getTokenType());
        data.put("expiresIn", response.getExpiresIn());

        return ResponseEntity.ok(SuccessResponse.of("註冊成功", data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SuccessResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshRequest) {
        log.info("Token 刷新請求");
        AuthResponse response = authService.refreshToken(refreshRequest);

        // 轉換為前端期望的格式
        Map<String, Object> data = new HashMap<>();
        data.put("token", response.getAccessToken());
        data.put("refreshToken", response.getRefreshToken());
        data.put("tokenType", response.getTokenType());
        data.put("expiresIn", response.getExpiresIn());

        return ResponseEntity.ok(SuccessResponse.of("Token 刷新成功", data));
    }
    
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        log.info("登出請求: {}", userPrincipal.getUsername());
        authService.logout(userPrincipal.getId());
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/me")
    public ResponseEntity<AuthResponse.UserInfo> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
                .id(userPrincipal.getId())
                .username(userPrincipal.getUsername())
                .email(userPrincipal.getEmail())
                .role(userPrincipal.getRole())
                .build();
        
        return ResponseEntity.ok(userInfo);
    }
}
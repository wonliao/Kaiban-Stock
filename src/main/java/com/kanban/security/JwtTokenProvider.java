package com.kanban.security;

import com.kanban.domain.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {
    
    private final SecretKey jwtSecret;
    private final int accessTokenExpirationMs;
    private final int refreshTokenExpirationMs;
    
    public JwtTokenProvider(
            @Value("${app.jwt.secret:mySecretKey}") String jwtSecret,
            @Value("${app.jwt.access-token-expiration-ms:3600000}") int accessTokenExpirationMs, // 1 hour
            @Value("${app.jwt.refresh-token-expiration-ms:2592000000}") int refreshTokenExpirationMs // 30 days
    ) {
        this.jwtSecret = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }
    
    public String generateAccessToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return generateAccessToken(userPrincipal.getId(), userPrincipal.getUsername(), userPrincipal.getRole());
    }
    
    public String generateAccessToken(String userId, String username, User.UserRole role) {
        Date expiryDate = new Date(System.currentTimeMillis() + accessTokenExpirationMs);
        
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role.name())
                .claim("type", "ACCESS")
                .issuedAt(new Date())
                .expiration(expiryDate)
                .signWith(jwtSecret)
                .compact();
    }
    
    public String generateRefreshToken(String userId) {
        Date expiryDate = new Date(System.currentTimeMillis() + refreshTokenExpirationMs);
        
        return Jwts.builder()
                .subject(userId)
                .claim("type", "REFRESH")
                .claim("jti", UUID.randomUUID().toString()) // JWT ID for uniqueness
                .issuedAt(new Date())
                .expiration(expiryDate)
                .signWith(jwtSecret)
                .compact();
    }
    
    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(jwtSecret)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.getSubject();
    }
    
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(jwtSecret)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.get("username", String.class);
    }
    
    public User.UserRole getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(jwtSecret)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        String role = claims.get("role", String.class);
        return User.UserRole.valueOf(role);
    }
    
    public String getTokenTypeFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(jwtSecret)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return claims.get("type", String.class);
    }
    
    public LocalDateTime getExpirationFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(jwtSecret)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        
        return LocalDateTime.ofInstant(
                claims.getExpiration().toInstant(),
                ZoneId.systemDefault()
        );
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(jwtSecret)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }
    
    public boolean isAccessToken(String token) {
        try {
            String tokenType = getTokenTypeFromToken(token);
            return "ACCESS".equals(tokenType);
        } catch (Exception e) {
            return false;
        }
    }
    
    public boolean isRefreshToken(String token) {
        try {
            String tokenType = getTokenTypeFromToken(token);
            return "REFRESH".equals(tokenType);
        } catch (Exception e) {
            return false;
        }
    }
    
    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
    
    public long getRefreshTokenExpirationMs() {
        return refreshTokenExpirationMs;
    }
}
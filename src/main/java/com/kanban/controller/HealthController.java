package com.kanban.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {
    
    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now());
        health.put("application", "Taiwan Stock Kanban Dashboard");
        health.put("version", "1.0.0-SNAPSHOT");
        
        // Check database connection
        try (Connection connection = dataSource.getConnection()) {
            health.put("database", "UP");
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("database_error", e.getMessage());
        }
        
        // Check Redis connection
        try {
            redisTemplate.opsForValue().set("health_check", "OK");
            String result = (String) redisTemplate.opsForValue().get("health_check");
            health.put("redis", "OK".equals(result) ? "UP" : "DOWN");
        } catch (Exception e) {
            health.put("redis", "DOWN");
            health.put("redis_error", e.getMessage());
        }
        
        return ResponseEntity.ok(health);
    }
}
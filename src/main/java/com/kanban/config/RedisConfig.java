package com.kanban.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
        template.afterPropertiesSet();
        return template;
    }
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(1)) // Default 1 minute TTL
                .serializeKeysWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
        
        // Different TTL for different cache types
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Stock snapshots - 60 seconds during market hours, 300 seconds after hours
        cacheConfigurations.put("stock-snapshot", defaultConfig.entryTtl(Duration.ofSeconds(60)));
        cacheConfigurations.put("stock-snapshot-afterhours", defaultConfig.entryTtl(Duration.ofSeconds(300)));
        
        // Technical indicators - 5 minutes (expensive to calculate)
        cacheConfigurations.put("technical-indicators", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // User data - 30 minutes (changes infrequently)
        cacheConfigurations.put("user-data", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Stock validation - 1 hour (stock list doesn't change often)
        cacheConfigurations.put("stock-validation", defaultConfig.entryTtl(Duration.ofHours(1)));
        
        // Historical data - 24 hours (historical data is stable)
        cacheConfigurations.put("historical-data", defaultConfig.entryTtl(Duration.ofHours(24)));
        
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
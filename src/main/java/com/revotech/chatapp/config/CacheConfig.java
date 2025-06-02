package com.revotech.chatapp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig {

    @Value("${app.cache.default-ttl:600}")
    private long defaultTtlSeconds;

    @Value("${app.cache.user-cache-ttl:1800}")
    private long userCacheTtlSeconds;

    @Value("${app.cache.message-cache-ttl:3600}")
    private long messageCacheTtlSeconds;

    // Redis Cache Manager - chỉ khi Redis có sẵn
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(defaultTtlSeconds))
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Specific cache configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // User cache - longer TTL
        cacheConfigurations.put("users", defaultConfig
                .entryTtl(Duration.ofSeconds(userCacheTtlSeconds)));

        // Message cache - even longer TTL
        cacheConfigurations.put("messages", defaultConfig
                .entryTtl(Duration.ofSeconds(messageCacheTtlSeconds)));

        // Online users cache - short TTL
        cacheConfigurations.put("onlineUsers", defaultConfig
                .entryTtl(Duration.ofSeconds(60)));

        // Room members cache
        cacheConfigurations.put("roomMembers", defaultConfig
                .entryTtl(Duration.ofSeconds(300)));

        // Conversation cache
        cacheConfigurations.put("conversations", defaultConfig
                .entryTtl(Duration.ofSeconds(900)));

        log.info("Redis Cache configuration initialized - Default TTL: {}s, User TTL: {}s, Message TTL: {}s",
                defaultTtlSeconds, userCacheTtlSeconds, messageCacheTtlSeconds);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    // Fallback In-Memory Cache Manager - khi Redis không có sẵn
    @Bean
    @ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = true, havingValue = "false")
    public CacheManager inMemoryCacheManager() {
        log.warn("Redis not configured, using in-memory cache manager");

        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(java.util.List.of(
                "users", "messages", "onlineUsers", "roomMembers", "conversations"
        ));

        return cacheManager;
    }
}
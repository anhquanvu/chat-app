package com.revotech.chatapp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
@Slf4j
public class RateLimitingConfig {

    @Value("${app.rate-limiting.enabled:false}")
    private boolean rateLimitingEnabled;

    @Value("${app.rate-limiting.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${app.rate-limiting.requests-per-hour:1000}")
    private int requestsPerHour;

    @Value("${app.rate-limiting.burst-capacity:10}")
    private int burstCapacity;

    @Bean
    @ConditionalOnProperty(name = "app.rate-limiting.enabled", havingValue = "true")
    public RateLimitingService rateLimitingService(@Autowired(required = false) RedisTemplate<String, Object> redisTemplate) {
        RateLimitingService service = new RateLimitingService(
                redisTemplate,
                rateLimitingEnabled,
                requestsPerMinute,
                requestsPerHour,
                burstCapacity
        );

        log.info("Rate limiting configured - Enabled: {}, RPM: {}, RPH: {}, Burst: {}",
                rateLimitingEnabled, requestsPerMinute, requestsPerHour, burstCapacity);

        return service;
    }

    @Bean
    @ConditionalOnProperty(name = "app.rate-limiting.enabled", havingValue = "false", matchIfMissing = true)
    public RateLimitingService disabledRateLimitingService() {
        log.info("Rate limiting is disabled");
        return new RateLimitingService(null, false, requestsPerMinute, requestsPerHour, burstCapacity);
    }

    // Rate limiting service implementation
    public static class RateLimitingService {
        private final RedisTemplate<String, Object> redisTemplate;
        private final boolean enabled;
        private final int requestsPerMinute;
        private final int requestsPerHour;
        private final int burstCapacity;

        // Fallback in-memory rate limiting if Redis is not available
        private final ConcurrentHashMap<String, RateLimitBucket> inMemoryLimits = new ConcurrentHashMap<>();
        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        public RateLimitingService(RedisTemplate<String, Object> redisTemplate,
                                   boolean enabled,
                                   int requestsPerMinute,
                                   int requestsPerHour,
                                   int burstCapacity) {
            this.redisTemplate = redisTemplate;
            this.enabled = enabled;
            this.requestsPerMinute = requestsPerMinute;
            this.requestsPerHour = requestsPerHour;
            this.burstCapacity = burstCapacity;

            // Clean up in-memory cache every minute
            if (enabled) {
                scheduler.scheduleAtFixedRate(this::cleanupInMemoryLimits, 1, 1, TimeUnit.MINUTES);
            }
        }

        public boolean isAllowed(String key) {
            if (!enabled) {
                return true;
            }

            // Nếu có Redis thì dùng Redis, không thì dùng in-memory
            if (redisTemplate != null) {
                try {
                    return isAllowedWithRedis(key);
                } catch (Exception e) {
                    log.warn("Redis rate limiting failed, falling back to in-memory: {}", e.getMessage());
                    return isAllowedInMemory(key);
                }
            } else {
                return isAllowedInMemory(key);
            }
        }

        private boolean isAllowedWithRedis(String key) {
            String minuteKey = "rate_limit:minute:" + key;
            String hourKey = "rate_limit:hour:" + key;

            // Check minute limit
            Long minuteCount = redisTemplate.opsForValue().increment(minuteKey);
            if (minuteCount == 1) {
                redisTemplate.expire(minuteKey, Duration.ofMinutes(1));
            }

            // Check hour limit
            Long hourCount = redisTemplate.opsForValue().increment(hourKey);
            if (hourCount == 1) {
                redisTemplate.expire(hourKey, Duration.ofHours(1));
            }

            // Allow burst capacity for minute limit
            boolean minuteAllowed = minuteCount <= (requestsPerMinute + burstCapacity);
            boolean hourAllowed = hourCount <= requestsPerHour;

            boolean allowed = minuteAllowed && hourAllowed;

            if (!allowed) {
                log.warn("Rate limit exceeded for key: {} (minute: {}/{}, hour: {}/{})",
                        key, minuteCount, requestsPerMinute + burstCapacity, hourCount, requestsPerHour);
            }

            return allowed;
        }

        private boolean isAllowedInMemory(String key) {
            RateLimitBucket bucket = inMemoryLimits.computeIfAbsent(key, k -> new RateLimitBucket());
            return bucket.isAllowed();
        }

        private void cleanupInMemoryLimits() {
            long currentTime = System.currentTimeMillis();
            inMemoryLimits.entrySet().removeIf(entry ->
                    currentTime - entry.getValue().getLastAccess() > TimeUnit.HOURS.toMillis(1)
            );
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getRequestsPerMinute() {
            return requestsPerMinute;
        }

        public int getRequestsPerHour() {
            return requestsPerHour;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        // Inner class for in-memory rate limiting
        private class RateLimitBucket {
            private final AtomicInteger minuteCount = new AtomicInteger(0);
            private final AtomicInteger hourCount = new AtomicInteger(0);
            private final AtomicLong lastMinuteReset = new AtomicLong(System.currentTimeMillis());
            private final AtomicLong lastHourReset = new AtomicLong(System.currentTimeMillis());
            private final AtomicLong lastAccess = new AtomicLong(System.currentTimeMillis());

            public boolean isAllowed() {
                long currentTime = System.currentTimeMillis();
                lastAccess.set(currentTime);

                // Reset minute counter if needed
                if (currentTime - lastMinuteReset.get() > TimeUnit.MINUTES.toMillis(1)) {
                    minuteCount.set(0);
                    lastMinuteReset.set(currentTime);
                }

                // Reset hour counter if needed
                if (currentTime - lastHourReset.get() > TimeUnit.HOURS.toMillis(1)) {
                    hourCount.set(0);
                    lastHourReset.set(currentTime);
                }

                int currentMinuteCount = minuteCount.incrementAndGet();
                int currentHourCount = hourCount.incrementAndGet();

                return currentMinuteCount <= (requestsPerMinute + burstCapacity) &&
                        currentHourCount <= requestsPerHour;
            }

            public long getLastAccess() {
                return lastAccess.get();
            }
        }
    }
}
package com.copago.test_hat.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

@TestConfiguration
@Profile("integration-test")
public class EmbeddedRedisConfig {
    @Value("${spring.redis.port}")
    private int redisPort;

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() {
        try {
            redisServer = RedisServer.builder()
                    .port(redisPort)
                    .setting("maxmemory 128M")
                    .build();
            redisServer.start();
        } catch (Exception e) {
            // 이미 실행 중인 경우 무시
            if (!e.getMessage().contains("Address already in use")) {
                throw e;
            }
        }
    }

    @PreDestroy
    public void stopRedis() {
        try {
            if (redisServer != null && redisServer.isActive()) {
                redisServer.stop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

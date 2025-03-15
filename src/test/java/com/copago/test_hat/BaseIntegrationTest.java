package com.copago.test_hat;

import com.copago.test_hat.config.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("integration-test")
@Testcontainers
@Import({TestConfig.class, TestRabbitMQConfig.class, TestSecurityConfig.class, TestWebSocketConfig.class})
@ContextConfiguration(initializers = BaseIntegrationTest.Initializer.class)
public abstract class BaseIntegrationTest {
    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Container
    static final RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.9.11-management"))
            .withExposedPorts(5672, 15672);

    @Container
    static final GenericContainer<?> redisContainer = new GenericContainer<>(DockerImageName.parse("redis:6.2.6-alpine"))
            .withExposedPorts(6379);

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.rabbitmq.host=" + rabbitMQContainer.getHost(),
                    "spring.rabbitmq.port=" + rabbitMQContainer.getMappedPort(5672),
                    "spring.redis.host=" + redisContainer.getHost(),
                    "spring.redis.port=" + redisContainer.getMappedPort(6379)
            ).applyTo(context.getEnvironment());
        }
    }

    @BeforeEach
    void setUp() {
        // 통합 테스트를 위한 공통 설정 및 데이터 준비
    }
}

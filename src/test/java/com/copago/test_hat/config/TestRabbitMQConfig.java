package com.copago.test_hat.config;

import jakarta.annotation.PostConstruct;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("integration-test")
public class TestRabbitMQConfig {
    /**
     * 테스트 환경에서 RabbitMQ 리스너 엔드포인트를 비활성화하기 위한 빈
     */
    @Bean
    public RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry() {
        return new RabbitListenerEndpointRegistry() {
            @Override
            public void start() {
                // 아무 동작도 하지 않음 - 리스너 시작 방지
            }
        };
    }

    /**
     * RabbitMQ 관리 빈
     */
    @Bean
    @Primary
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // 자동 시작 비활성화
        admin.setAutoStartup(false);
        return admin;
    }
}

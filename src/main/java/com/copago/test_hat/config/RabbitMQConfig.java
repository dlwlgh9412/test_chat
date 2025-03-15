package com.copago.test_hat.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {
    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username:dlwlgh9412}")
    private String username;

    @Value("${spring.rabbitmq.password:wlgh7078!!}")
    private String password;

    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String CHAT_QUEUE = "chat.queue";
    public static final String CHAT_ROUTING_KEY = "chat.message";

    public static final String STATUS_EXCHANGE = "status.exchange";
    public static final String STATUS_QUEUE = "status.queue";
    public static final String STATUS_ROUTING_KEY = "status.update";

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        return connectionFactory;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
        // Jackson 객체 매퍼 설정
//        ObjectMapper objectMapper = new ObjectMapper();
//        objectMapper.registerModule(new JavaTimeModule());
//        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
//
//        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
//        converter.setCreateMessageIds(true);
//        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(CHAT_EXCHANGE);
    }

    @Bean
    public Queue chatQueue() {
        return QueueBuilder.durable(CHAT_QUEUE)
                .withArgument("x-message-ttl", 60000)
                .build();
    }

    @Bean
    public Binding chatBinding() {
        return BindingBuilder.bind(chatQueue()).to(chatExchange()).with(CHAT_ROUTING_KEY);
    }

    @Bean
    public DirectExchange statusExchange() {
        return new DirectExchange(STATUS_EXCHANGE);
    }

    @Bean
    public Queue statusQueue() {
        return QueueBuilder.durable(STATUS_QUEUE)
                .withArgument("x-message-ttl", 60000)
                .build();
    }

    @Bean
    public Binding statusBinding() {
        return BindingBuilder.bind(statusQueue()).to(statusExchange()).with(STATUS_ROUTING_KEY);
    }
}

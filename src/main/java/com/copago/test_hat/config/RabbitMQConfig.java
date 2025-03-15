package com.copago.test_hat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableRabbit
public class RabbitMQConfig {
    // 기본 설정 값
    @Value("${spring.rabbitmq.host:localhost}")
    private String host;

    @Value("${spring.rabbitmq.port:5672}")
    private int port;

    @Value("${spring.rabbitmq.username:guest}")
    private String username;

    @Value("${spring.rabbitmq.password:guest}")
    private String password;

    // 연결 풀 크기 설정
    @Value("${spring.rabbitmq.connection.channel-cache-size:25}")
    private int channelCacheSize;

    // 컨슈머 동시성 설정
    @Value("${spring.rabbitmq.listener.simple.concurrency:5}")
    private int concurrentConsumers;

    @Value("${spring.rabbitmq.listener.simple.max-concurrency:20}")
    private int maxConcurrentConsumers;

    // 재시도 설정
    @Value("${spring.rabbitmq.listener.simple.retry.enabled:true}")
    private boolean retryEnabled;

    @Value("${spring.rabbitmq.listener.simple.retry.max-attempts:3}")
    private int maxAttempts;

    // 교환기, 큐, 라우팅 키 상수
    public static final String CHAT_EXCHANGE = "chat.exchange";
    public static final String CHAT_QUEUE = "chat.queue";
    public static final String CHAT_ROUTING_KEY = "chat.message";

    public static final String DLX_EXCHANGE = "chat.dlx";
    public static final String DLQ_QUEUE = "chat.dlq";

    public static final String STATUS_EXCHANGE = "status.exchange";
    public static final String STATUS_QUEUE = "status.queue";
    public static final String STATUS_ROUTING_KEY = "status.update";

    // 채팅방별 큐 생성을 위한 접두사
    public static final String ROOM_QUEUE_PREFIX = "chat.room.";

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);

        // 연결 풀 크기 설정
        connectionFactory.setChannelCacheSize(channelCacheSize);

        // 게시자 확인 활성화 (메시지 전송 보장)
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        return connectionFactory;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        // RabbitAdmin 자동 시작 활성화
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RetryOperationsInterceptor retryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);

        // 동시성 설정
        factory.setConcurrentConsumers(concurrentConsumers);
        factory.setMaxConcurrentConsumers(maxConcurrentConsumers);

        // 메시지 처리 설정
        factory.setPrefetchCount(10);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);

        // 재시도 설정
        if (retryEnabled) {
            factory.setAdviceChain(retryInterceptor());
        }

        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);

        // 메시지 전송 보장 설정
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("Message send failed: {}", cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> {
            log.error("Message returned: {}", returned.getMessage());
        });

        // 기본 교환기 설정
        rabbitTemplate.setExchange(CHAT_EXCHANGE);

        return rabbitTemplate;
    }

    // Dead Letter Exchange 설정
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE)
                .build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dead-letter");
    }

    // 채팅 메시지 교환기 및 큐 설정
    @Bean
    public DirectExchange chatExchange() {
        return new DirectExchange(CHAT_EXCHANGE);
    }

    @Bean
    public Queue chatQueue() {
        Map<String, Object> args = new HashMap<>();
        // TTL 설정 (1분)
        args.put("x-message-ttl", 60000);
        // Dead Letter 설정
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "dead-letter");

        return QueueBuilder.durable(CHAT_QUEUE)
                .withArguments(args)
                .build();
    }

    @Bean
    public Binding chatBinding() {
        return BindingBuilder.bind(chatQueue())
                .to(chatExchange())
                .with(CHAT_ROUTING_KEY);
    }

    // 상태 업데이트 교환기 및 큐 설정
    @Bean
    public DirectExchange statusExchange() {
        return new DirectExchange(STATUS_EXCHANGE);
    }

    @Bean
    public Queue statusQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", 60000);
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "dead-letter");

        return QueueBuilder.durable(STATUS_QUEUE)
                .withArguments(args)
                .build();
    }

    @Bean
    public Binding statusBinding() {
        return BindingBuilder.bind(statusQueue())
                .to(statusExchange())
                .with(STATUS_ROUTING_KEY);
    }
}

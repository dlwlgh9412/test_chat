package com.copago.test_hat.messaging;

import com.copago.test_hat.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 동적 RabbitMQ 리스너 구성
 * 채팅방이 생성될 때 해당 채팅방의 큐에 대한 리스너를 동적으로 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicListenerConfig {
    private final ConnectionFactory connectionFactory;
    private final RabbitAdmin rabbitAdmin;
    private final RoomQueueManager roomQueueManager;
    private final MessageHandlerService messageHandlerService;

    private final ConcurrentHashMap<Long, SimpleMessageListenerContainer> roomListeners = new ConcurrentHashMap<>();

    /**
     * 기본 채팅 큐 리스너 설정
     */
    @Bean
    public SimpleMessageListenerContainer defaultMessageListenerContainer() {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(RabbitMQConfig.CHAT_QUEUE);

        MessageListenerAdapter listenerAdapter = new MessageListenerAdapter(messageHandlerService);
        listenerAdapter.setDefaultListenerMethod("receiveMessage");
        container.setMessageListener(listenerAdapter);

        log.info("기본 채팅 큐 리스너 초기화");
        return container;
    }

    /**
     * 특정 채팅방에 대한 메시지 리스너 생성
     * @param roomId 채팅방 ID
     */
    public void createListenerForRoom(Long roomId) {
        // 이미 리스너가 있으면 생성하지 않음
        if (roomListeners.containsKey(roomId)) {
            log.debug("채팅방 {}의 리스너가 이미 존재합니다", roomId);
            return;
        }

        try {
            // 채팅방 큐 생성 (존재하지 않는 경우)
            Queue queue = roomQueueManager.createRoomQueueIfNotExists(roomId);

            // 메시지 리스너 어댑터 설정
            MessageListenerAdapter listenerAdapter = new MessageListenerAdapter(messageHandlerService);
            listenerAdapter.setDefaultListenerMethod("receiveMessage");

            // 리스너 컨테이너 생성 및 설정
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.setQueueNames(queue.getName());
            container.setMessageListener(listenerAdapter);
            container.start();

            // 리스너 등록
            roomListeners.put(roomId, container);
            log.info("채팅방 {}의 동적 리스너가 생성되었습니다", roomId);

        } catch (Exception e) {
            log.error("채팅방 {}의 리스너 생성 중 오류 발생: {}", roomId, e.getMessage(), e);
        }
    }

    /**
     * 채팅방 리스너 제거
     * @param roomId 채팅방 ID
     */
    public void removeListenerForRoom(Long roomId) {
        SimpleMessageListenerContainer container = roomListeners.remove(roomId);
        if (container != null) {
            container.stop();
            log.info("채팅방 {}의 리스너가 제거되었습니다", roomId);
        }
    }
}

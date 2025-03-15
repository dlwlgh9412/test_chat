package com.copago.test_hat.messaging;

import com.copago.test_hat.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채팅방별 큐 동적 생성 관리자
 * 채팅방 수가 많아질 경우 채팅방별로 별도 큐를 생성하여 확장성 확보
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomQueueManager {
    private final RabbitAdmin rabbitAdmin;
    private final ConcurrentHashMap<Long, Queue> roomQueues = new ConcurrentHashMap<>();

    /**
     * 모든 채팅방 큐 이름 배열 반환
     * @RabbitListener 어노테이션에서 사용
     */
    public String[] allQueueNames() {
        if (roomQueues.isEmpty()) {
            // 기본 큐만 반환
            return new String[] { RabbitMQConfig.CHAT_QUEUE };
        }

        // 모든 채팅방 큐 이름 수집
        return roomQueues.values().stream()
                .map(Queue::getName)
                .toArray(String[]::new);
    }

    /**
     * 특정 채팅방을 위한 큐 이름 생성
     */
    private String getRoomQueueName(Long roomId) {
        return RabbitMQConfig.ROOM_QUEUE_PREFIX + roomId;
    }

    /**
     * 특정 채팅방을 위한 라우팅 키 생성
     */
    private String getRoomRoutingKey(Long roomId) {
        return "chat.room." + roomId;
    }

    /**
     * 채팅방 큐가 존재하는지 확인
     */
    public boolean roomQueueExists(Long roomId) {
        return roomQueues.containsKey(roomId);
    }

    /**
     * 채팅방용 큐 생성 (존재하지 않는 경우)
     */
    public Queue createRoomQueueIfNotExists(Long roomId) {
        return roomQueues.computeIfAbsent(roomId, id -> {
            String queueName = getRoomQueueName(id);

            // Dead Letter 설정
            Map<String, Object> args = new HashMap<>();
            args.put("x-message-ttl", 86400000); // 24시간 (1일)
            args.put("x-dead-letter-exchange", RabbitMQConfig.DLX_EXCHANGE);
            args.put("x-dead-letter-routing-key", "dead-letter");

            Queue queue = QueueBuilder.durable(queueName)
                    .withArguments(args)
                    .build();

            // 큐 선언
            rabbitAdmin.declareQueue(queue);

            // 바인딩 선언
            Binding binding = BindingBuilder.bind(queue)
                    .to(new DirectExchange(RabbitMQConfig.CHAT_EXCHANGE))
                    .with(getRoomRoutingKey(id));
            rabbitAdmin.declareBinding(binding);

            log.info("Created queue for chat room: {}", id);
            return queue;
        });
    }

    /**
     * 채팅방 큐 삭제
     */
    public void removeRoomQueue(Long roomId) {
        String queueName = getRoomQueueName(roomId);
        rabbitAdmin.deleteQueue(queueName);
        roomQueues.remove(roomId);
        log.info("Removed queue for chat room: {}", roomId);
    }

    /**
     * 특정 채팅방의 라우팅 키 반환
     */
    public String getRoutingKeyForRoom(Long roomId) {
        // 큐가 없으면 생성
        createRoomQueueIfNotExists(roomId);
        return getRoomRoutingKey(roomId);
    }
}

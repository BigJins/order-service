package allmart.orderservice.adapter.kafka.dto;

import java.time.LocalDateTime;

/**
 * delivery.completed.v1 Kafka 메시지 DTO
 */
public record DeliveryCompletedMessage(
        Long deliveryId,
        Long orderId,
        Long buyerId,
        long totalAmount,
        LocalDateTime completedAt
) {}

package allmart.orderservice.adapter.kafka.dto;

import allmart.orderservice.domain.order.Order;
import jakarta.annotation.Nullable;

import java.time.LocalDateTime;

/**
 * order.failed.v1 / order.confirmed.v1 Kafka 이벤트 페이로드
 * 상태 변경만 필요한 경우 사용하는 공통 페이로드.
 */
public record OrderStatusChangedPayload(
        String orderId,
        String status,
        @Nullable LocalDateTime paidAt,
        @Nullable LocalDateTime confirmedAt,
        @Nullable LocalDateTime canceledAt
) {
    public static OrderStatusChangedPayload from(Order order) {
        return new OrderStatusChangedPayload(
                String.valueOf(order.getId()),
                order.getStatus().name(),
                order.getPaidAt(),
                order.getConfirmedAt(),
                order.getCanceledAt()
        );
    }
}

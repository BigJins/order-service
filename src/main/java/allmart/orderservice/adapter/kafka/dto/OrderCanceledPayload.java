package allmart.orderservice.adapter.kafka.dto;

import allmart.orderservice.domain.order.Order;

import java.time.LocalDateTime;

/**
 * order.canceled.v1 Kafka 이벤트 페이로드
 *
 * 소비자:
 *  - inventory-service: tossOrderId로 RESERVED 재고 해제 (AVAILABLE 복귀)
 *  - pay-service: 환불 처리 (PAID 이후 취소 시 — 현재 미구현)
 *  - MongoDB Sink Connector: order-query-service 상태 갱신
 */
public record OrderCanceledPayload(
        Long orderId,
        String tossOrderId,
        Long buyerId,
        long cancelAmount,
        LocalDateTime canceledAt
) {
    public static OrderCanceledPayload from(Order order) {
        return new OrderCanceledPayload(
                order.getId(),
                order.getTossOrderId(),
                order.getBuyerId(),
                order.getTotalAmount().amount(),
                order.getCanceledAt()
        );
    }
}

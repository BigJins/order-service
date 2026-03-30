package allmart.orderservice.application.required;

import allmart.orderservice.domain.order.Order;

/**
 * Outbox 이벤트 저장 포트 — 구현은 adapter 레이어에서 담당
 */
public interface OutboxEventPublisher {
    void publishOrderPaid(Order order);
}

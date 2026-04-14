package allmart.orderservice.application.event.created;

import allmart.orderservice.domain.order.Order;

/**
 * 주문 생성 완료 도메인 이벤트.
 * OrderModifyService.create() → ApplicationEventPublisher → 각 핸들러로 전파.
 * BEFORE_COMMIT 단계에서 처리되므로 Outbox 저장이 주문 저장과 동일 트랜잭션에 포함된다.
 */
public record OrderCreatedEvent(Order order) {}

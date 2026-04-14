package allmart.orderservice.application.event.confirmed;

import allmart.orderservice.domain.order.Order;

/** 배달 완료 → 주문 최종 확정 이벤트 */
public record OrderConfirmedEvent(Order order) {}

package allmart.orderservice.application.event.canceled;

import allmart.orderservice.domain.order.Order;

/** 주문 취소 이벤트 */
public record OrderCanceledEvent(Order order) {}

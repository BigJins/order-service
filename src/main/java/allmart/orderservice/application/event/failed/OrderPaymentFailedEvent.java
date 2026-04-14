package allmart.orderservice.application.event.failed;

import allmart.orderservice.domain.order.Order;

/** 결제 실패 이벤트 */
public record OrderPaymentFailedEvent(Order order) {}

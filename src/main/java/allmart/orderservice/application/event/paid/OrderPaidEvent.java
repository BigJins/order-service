package allmart.orderservice.application.event.paid;

import allmart.orderservice.domain.order.Order;

/** 결제 완료 이벤트 (CARD 결제 승인, CASH_ON_DELIVERY는 OrderCreatedEvent로 처리) */
public record OrderPaidEvent(Order order) {}

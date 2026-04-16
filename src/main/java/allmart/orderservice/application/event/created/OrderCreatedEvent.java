package allmart.orderservice.application.event.created;

import allmart.orderservice.domain.order.OrderPayMethod;

/**
 * 주문 생성 완료 도메인 이벤트 — Zero Payload.
 * orderId + payMethod만 전달. 핸들러가 OrderRepository에서 전체 Order를 재조회한다.
 * payMethod는 OnDeliveryPaymentEventHandler가 후불 여부를 판단하는 데 필요.
 */
public record OrderCreatedEvent(Long orderId, OrderPayMethod payMethod) {}

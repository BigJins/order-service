package allmart.orderservice.application.event.paid;

/**
 * 결제 완료 도메인 이벤트 — Zero Payload.
 * orderId만 전달. 핸들러가 OrderRepository에서 전체 Order를 재조회한다.
 */
public record OrderPaidEvent(Long orderId) {}

package allmart.orderservice.application.event.failed;

/**
 * 결제 실패 도메인 이벤트 — Zero Payload.
 * orderId만 전달. 핸들러가 OrderRepository에서 전체 Order를 재조회한다.
 */
public record OrderPaymentFailedEvent(Long orderId) {}

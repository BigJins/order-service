package allmart.orderservice.application.event.confirmed;

/**
 * 배달 완료 → 주문 최종 확정 도메인 이벤트 — Zero Payload.
 * orderId만 전달. 핸들러가 OrderRepository에서 전체 Order를 재조회한다.
 */
public record OrderConfirmedEvent(Long orderId) {}

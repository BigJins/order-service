package allmart.orderservice.domain.order;

/** 주문 조회 실패 시 발생하는 도메인 예외 — GlobalExceptionHandler에서 404로 매핑 */
public class OrderNotFoundException extends RuntimeException {

    /** orderId(DB PK) 기준 조회 실패 */
    public OrderNotFoundException(Long orderId) {
        super("주문을 찾을 수 없습니다: orderId=" + orderId);
    }

    /** tossOrderId 기준 조회 실패 (Kafka 메시지 처리 시 사용) */
    public OrderNotFoundException(String tossOrderId) {
        super("주문을 찾을 수 없습니다: tossOrderId=" + tossOrderId);
    }
}

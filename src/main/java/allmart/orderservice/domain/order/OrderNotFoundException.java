package allmart.orderservice.domain.order;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("주문을 찾을 수 없습니다: orderId=" + orderId);
    }

    public OrderNotFoundException(String tossOrderId) {
        super("주문을 찾을 수 없습니다: tossOrderId=" + tossOrderId);
    }
}

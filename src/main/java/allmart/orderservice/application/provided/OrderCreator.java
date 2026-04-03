package allmart.orderservice.application.provided;

import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import jakarta.validation.Valid;

/**
 * 주문의 생성과 관련된 기능을 제공한다
 */
public interface OrderCreator {
    Order create(@Valid OrderCreateRequest orderCreateRequest);

    void applyPaid(String tossOrderId, String paymentKey, long amount);

    void applyPaymentFailed(String tossOrderId, String paymentKey, long amount);

    void applyDeliveryCompleted(Long orderId);

    /** 현금 선불 — 판매자가 현금 수령 확인 */
    void confirmCashPayment(Long orderId);

    /** 결제 실패 후 재결제 요청 — buyerId로 본인 주문 여부 검증 */
    void retryPayment(Long orderId, Long buyerId);
}

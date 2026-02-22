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

}

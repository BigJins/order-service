package allmart.orderservice.application.provided;

import allmart.orderservice.domain.order.Money;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import jakarta.validation.Valid;

/**
 * 주문의 생성과 관련된 기능을 제공한다
 */
public interface OrderCreator {
    /** 신규 주문 생성 — 재고 예약 및 Outbox 저장 포함 */
    Order create(@Valid OrderCreateRequest orderCreateRequest);

    /** 결제 성공 처리 (PENDING_PAYMENT → PAID) */
    void applyPaid(String tossOrderId, String paymentKey, Money amount);

    /** 결제 실패 처리 (PENDING_PAYMENT → PAYMENT_FAILED) */
    void applyPaymentFailed(String tossOrderId, String paymentKey, Money amount);

    /** 배달 완료 처리 (PAID → CONFIRMED) */
    void applyDeliveryCompleted(Long orderId);

    /** 결제 실패 후 재결제 요청 — buyerId로 본인 주문 여부 검증 */
    void retryPayment(Long orderId, Long buyerId);

    /** 주문 취소 — PENDING_PAYMENT 상태만 허용, buyerId로 본인 주문 여부 검증 */
    void cancelOrder(Long orderId, Long buyerId);

    /** 재고 부족 시 시스템이 주문 자동 취소 — buyerId 검증 없음 */
    void cancelByReserveFailed(String tossOrderId);
}

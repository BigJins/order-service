package allmart.orderservice.application.command;

import allmart.orderservice.domain.order.Money;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import jakarta.validation.Valid;

/**
 * 주문 Command(쓰기) 유스케이스 포트.
 * 주문 생성, 결제 상태 전이, 재결제, 취소 기능을 정의한다.
 * 구현체: OrderModifyService
 */
public interface OrderCommandUseCase {

    /** 신규 주문 생성 — 가격 검증 후 Order 저장, 도메인 이벤트 발행 */
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

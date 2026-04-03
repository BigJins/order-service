package allmart.orderservice.domain;

import allmart.orderservice.domain.order.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    Order order;
    Long buyerId;
    OrderLine orderLine;
    List<OrderLine> orderLines;
    DeliverySnapshot deliverySnapshot;
    MartSnapshot martSnapshot;
    OrderCreateRequest req;

    @BeforeEach
    void setUp() {
        buyerId = 1L;
        orderLine = new OrderLine(100L, "서귀포 감귤", new Money(15000), 2);
        orderLines = List.of(orderLine);
        deliverySnapshot = new DeliverySnapshot("47352", "부산광역시 부산진구", "범내골역 4번 출구");
        martSnapshot = new MartSnapshot(1L, "부산 범내골 마트", null);
        req = new OrderCreateRequest(buyerId, OrderPayMethod.CARD, orderLines, deliverySnapshot, martSnapshot, null);
        order = Order.create(req);
    }

    @Test
    void createOrder() {
        assertThat(order.getBuyerId()).isEqualTo(buyerId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getTossOrderId()).isNotBlank();
        assertThat(order.getTotalAmount().amount()).isEqualTo(33000); // 상품 30000 + 배송비 3000
        assertThat(order.getPayMethod()).isEqualTo(OrderPayMethod.CARD);
        assertThat(order.getMartSnapshot().martId()).isEqualTo(1L);
        assertThat(order.getChargeLines()).hasSize(2);
    }

    @Test
    @DisplayName("금액이 일치하면 PAID 상태로 변경된다")
    void markAsPaid_whenAmountMatches_thenStatusBecomePaid() {
        order.markAsPaid(33000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("결제 금액이 주문 금액과 다르면 IllegalArgumentException이 발생한다")
    void markAsPaid_whenAmountMismatches_thenThrows() {
        assertThatThrownBy(() -> order.markAsPaid(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 금액 불일치");
    }

    @Test
    @DisplayName("이미 PAID 상태에서 재결제 시도하면 조용히 무시된다 (Kafka 중복 메시지 멱등 처리)")
    void markAsPaid_whenAlreadyPaid_thenIgnored() {
        order.markAsPaid(33000L);
        order.markAsPaid(33000L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("5만원 이상 주문 시 배송비 무료")
    void createOrder_whenOver50000_thenFreeDelivery() {
        OrderLine bigLine = new OrderLine(1L, "고급 상품", new Money(30000), 2);
        OrderCreateRequest bigReq = new OrderCreateRequest(
                buyerId, OrderPayMethod.CARD, List.of(bigLine), deliverySnapshot, martSnapshot, null);
        Order bigOrder = Order.create(bigReq);
        assertThat(bigOrder.getTotalAmount().amount()).isEqualTo(60000);
    }

    @Test
    @DisplayName("CASH_ON_DELIVERY 주문은 생성 시 즉시 PAID 상태")
    void createOrder_cashOnDelivery_isAlreadyPaid() {
        OrderCreateRequest codReq = new OrderCreateRequest(
                buyerId, OrderPayMethod.CASH_ON_DELIVERY, orderLines, deliverySnapshot, martSnapshot, null);
        Order codOrder = Order.create(codReq);
        assertThat(codOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(codOrder.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 실패 처리 시 PAYMENT_FAILED 상태로 변경된다")
    void markPaymentFailed_thenStatusBecomesPaymentFailed() {
        order.markPaymentFailed();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("이미 PAYMENT_FAILED 상태에서 재호출해도 멱등 처리된다")
    void markPaymentFailed_whenAlreadyFailed_thenIgnored() {
        order.markPaymentFailed();
        order.markPaymentFailed();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("결제 완료 후 배달 완료 시 CONFIRMED 상태로 변경된다")
    void markAsCompleted_whenPaid_thenStatusBecomesConfirmed() {
        order.markAsPaid(33000L);
        order.markAsCompleted();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("PAID 상태가 아닐 때 완료 처리하면 예외가 발생한다")
    void markAsCompleted_whenNotPaid_thenThrows() {
        assertThatThrownBy(() -> order.markAsCompleted())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 CONFIRMED 상태에서 재호출해도 멱등 처리된다")
    void markAsCompleted_whenAlreadyConfirmed_thenIgnored() {
        order.markAsPaid(33000L);
        order.markAsCompleted();
        order.markAsCompleted();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("현금 선불 확인 시 PAID 상태로 변경된다")
    void confirmCashPayment_thenStatusBecomesPaid() {
        OrderCreateRequest cashReq = new OrderCreateRequest(
                buyerId, OrderPayMethod.CASH_PREPAID, orderLines, deliverySnapshot, martSnapshot, null);
        Order cashOrder = Order.create(cashReq);

        cashOrder.confirmCashPayment();

        assertThat(cashOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(cashOrder.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("CASH_PREPAID 외 결제수단에 현금 선불 확인하면 예외가 발생한다")
    void confirmCashPayment_whenNotCashPrepaid_thenThrows() {
        assertThatThrownBy(() -> order.confirmCashPayment())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("결제 실패 후 재결제 요청 시 PENDING_PAYMENT 로 복귀하고 tossOrderId가 재발급된다")
    void retryPayment_thenStatusReturnsToPendingAndTossOrderIdRenewed() {
        String originalTossOrderId = order.getTossOrderId();
        order.markPaymentFailed();

        order.retryPayment();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getTossOrderId()).isNotEqualTo(originalTossOrderId);
    }

    @Test
    @DisplayName("PAYMENT_FAILED 상태가 아닐 때 재결제 요청하면 예외가 발생한다")
    void retryPayment_whenNotPaymentFailed_thenThrows() {
        assertThatThrownBy(() -> order.retryPayment())
                .isInstanceOf(IllegalStateException.class);
    }
}

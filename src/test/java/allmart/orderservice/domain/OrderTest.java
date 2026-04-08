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
    MartDeliveryConfig deliveryConfig;
    OrderCreateRequest req;

    @BeforeEach
    void setUp() {
        buyerId = 1L;
        orderLine = new OrderLine(100L, "서귀포 감귤", new Money(15000), 2, "TAXABLE");
        orderLines = List.of(orderLine);
        deliverySnapshot = new DeliverySnapshot("47352", "부산광역시 부산진구", "범내골역 4번 출구");
        martSnapshot = new MartSnapshot(1L, "부산 범내골 마트", null);
        deliveryConfig = MartDeliveryConfig.create(1L, 3_000L, 50_000L);
        req = new OrderCreateRequest(buyerId, OrderPayMethod.CARD, orderLines, deliverySnapshot, martSnapshot, null);
        order = Order.create(req, deliveryConfig);
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
        assertThat(order.getChargeLines()).hasSize(4); // SUBTOTAL, DELIVERY_FEE, DELIVERY_SUPPLY, DELIVERY_VAT
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
    @DisplayName("주문 생성 시 배달료 3,000원 기준으로 공급가액과 부가세가 자동 계산된다")
    void createOrder_thenDeliveryTaxCalculated() {
        // 3,000 / 1.1 = 2,727.27 → 반올림 2,727 / 부가세 273
        assertThat(order.getDeliveryFee().amount()).isEqualTo(3_000);
        assertThat(order.getDeliverySupply().amount()).isEqualTo(2_727);
        assertThat(order.getDeliveryVat().amount()).isEqualTo(273);
    }

    @Test
    @DisplayName("5만원 이상 주문 시 배달료 무료이고 공급가액, 부가세 모두 0원이다")
    void createOrder_whenOver50000_thenFreeDelivery() {
        OrderLine bigLine = new OrderLine(1L, "고급 상품", new Money(30000), 2, "TAXABLE");
        OrderCreateRequest bigReq = new OrderCreateRequest(
                buyerId, OrderPayMethod.CARD, List.of(bigLine), deliverySnapshot, martSnapshot, null);
        Order bigOrder = Order.create(bigReq, deliveryConfig);

        assertThat(bigOrder.getTotalAmount().amount()).isEqualTo(60_000);
        assertThat(bigOrder.getFreeDelivery()).isTrue();
        assertThat(bigOrder.getDeliveryFee().amount()).isZero();
        assertThat(bigOrder.getDeliverySupply().amount()).isZero();
        assertThat(bigOrder.getDeliveryVat().amount()).isZero();
    }

    @Test
    @DisplayName("CASH_ON_DELIVERY 주문은 생성 시 즉시 PAID 상태")
    void createOrder_cashOnDelivery_isAlreadyPaid() {
        OrderCreateRequest codReq = new OrderCreateRequest(
                buyerId, OrderPayMethod.CASH_ON_DELIVERY, orderLines, deliverySnapshot, martSnapshot, null);
        Order codOrder = Order.create(codReq, deliveryConfig);
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
        Order cashOrder = Order.create(cashReq, deliveryConfig);

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

    // ── 배달료 세금 계산 (applyDeliveryFee 단위 테스트) ─────────────────────

    @Test
    @DisplayName("배달료 3,300원 적용 시 공급가액 3,000원, 부가세 300원이 계산된다")
    void applyDeliveryFee_3300_thenSupply3000Vat300() {
        order.applyDeliveryFee(Money.of(3_300));

        assertThat(order.getDeliveryFee().amount()).isEqualTo(3_300);
        assertThat(order.getDeliverySupply().amount()).isEqualTo(3_000);
        assertThat(order.getDeliveryVat().amount()).isEqualTo(300);
    }

    @Test
    @DisplayName("배달료 1,100원 적용 시 공급가액 1,000원, 부가세 100원이 계산된다")
    void applyDeliveryFee_1100_thenSupply1000Vat100() {
        order.applyDeliveryFee(Money.of(1_100));

        assertThat(order.getDeliveryFee().amount()).isEqualTo(1_100);
        assertThat(order.getDeliverySupply().amount()).isEqualTo(1_000);
        assertThat(order.getDeliveryVat().amount()).isEqualTo(100);
    }

    @Test
    @DisplayName("배달료가 1.1로 나누어 떨어지지 않으면 공급가액은 반올림되고 공급가액+부가세=배달료이다")
    void applyDeliveryFee_notDivisible_thenSupplyRounded() {
        // 1,000 / 1.1 = 909.09... → 반올림 909, 부가세 91
        order.applyDeliveryFee(Money.of(1_000));

        assertThat(order.getDeliverySupply().amount()).isEqualTo(909);
        assertThat(order.getDeliveryVat().amount()).isEqualTo(91);
        assertThat(order.getDeliverySupply().amount() + order.getDeliveryVat().amount()).isEqualTo(1_000);
    }

    @Test
    @DisplayName("freeDelivery=true 인 경우 배달료를 전달해도 공급가액과 부가세 모두 0원이다")
    void applyDeliveryFee_whenFreeDelivery_thenAllZero() {
        // given: 5만원 이상 주문으로 freeDelivery=true 인 order
        OrderLine bigLine = new OrderLine(1L, "고급 상품", new Money(30_000), 2, "TAXABLE");
        Order freeOrder = Order.create(new OrderCreateRequest(
                buyerId, OrderPayMethod.CARD, List.of(bigLine), deliverySnapshot, martSnapshot, null), deliveryConfig);

        // when: 배달료를 다시 적용해도 freeDelivery 플래그가 우선
        freeOrder.applyDeliveryFee(Money.of(3_000));

        // then
        assertThat(freeOrder.getDeliveryFee().amount()).isZero();
        assertThat(freeOrder.getDeliverySupply().amount()).isZero();
        assertThat(freeOrder.getDeliveryVat().amount()).isZero();
    }

    @Test
    @DisplayName("배달료가 null이면 공급가액과 부가세 모두 0원이다")
    void applyDeliveryFee_null_thenAllZero() {
        order.applyDeliveryFee(null);

        assertThat(order.getDeliveryFee().amount()).isZero();
        assertThat(order.getDeliverySupply().amount()).isZero();
        assertThat(order.getDeliveryVat().amount()).isZero();
    }
}

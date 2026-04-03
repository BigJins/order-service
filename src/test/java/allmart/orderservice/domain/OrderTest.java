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
}

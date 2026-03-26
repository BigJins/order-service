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
    ShippingInfo shippingInfo;
    OrderCreateRequest req;

    @BeforeEach
    void setUp() {
        buyerId = 1L;
        orderLine = new OrderLine(100L, "서귀포 감귤", new Money(15000), 2);
        orderLines = List.of(orderLine);
        shippingInfo = new ShippingInfo(
                "홍길동",
                "01012345678",
                new Address("47352", "부산광역시", "범내골역4번출구"),
                null);
        OrderCreateRequest req = new OrderCreateRequest(buyerId, orderLines, shippingInfo);
        order = Order.create(req);
    }

    @Test
    void createOrder() {

        assertThat(order.getBuyerId()).isEqualTo(buyerId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getTossOrderId()).isNotBlank();
        assertThat(order.getTotalAmount().amount()).isEqualTo(30000);
    }
    
    @Test
    @DisplayName("금액이 일치하면 PAID 상태로 변경된다")
    void markAsPaid_whenAmountMatches_thenStatusBecomePaid() {
        // given: totalAmount = 15000 * 2 = 30000
        // when
        order.markAsPaid(30000L);
        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("결제 금액이 주문 금액과 다르면 IllegalArgumentException이 발생한다")
    void markAsPaid_whenAmountMismatches_thenThrows() {
        // given: totalAmount = 30000, 변조된 금액 = 1
        assertThatThrownBy(() -> order.markAsPaid(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결제 금액 불일치");
    }

    @Test
    @DisplayName("이미 PAID 상태에서 재결제 시도하면 조용히 무시된다 (Kafka 중복 메시지 멱등 처리)")
    void markAsPaid_whenAlreadyPaid_thenIgnored() {
        order.markAsPaid(30000L);

        // Kafka at-least-once: 동일 메시지 재수신 시 예외 없이 무시
        order.markAsPaid(30000L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }
}
package allmart.orderservice.domain;

import allmart.orderservice.domain.order.*;
import org.junit.jupiter.api.BeforeEach;
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
    void changeOrderStatus() {
        order.markAsPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getBuyerId()).isEqualTo(1L);

    }

    @Test
    void changeOrderStatusFail() {
        order.markAsPaid();

        assertThatThrownBy(() -> order.markAsPaid())
                .isInstanceOf(IllegalStateException.class);
    }
}
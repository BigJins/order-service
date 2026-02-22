package allmart.orderservice.application.provided;

import allmart.orderservice.domain.order.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static allmart.orderservice.domain.order.OrderStatus.PENDING_PAYMENT;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
record OrderCreatorTest(OrderCreator orderCreator, EntityManager entityManager) {

    @Test
    void createOrder() {
        OrderCreateRequest req = new OrderCreateRequest(23L,
                List.of(new OrderLine(1L, "사과", new Money(1000), 2)),
                new ShippingInfo("김아무개", "01012345678", new Address("47352", "부산광역시", "범내골역4번출구"), "잘 부탁드려요"));

        Order order = orderCreator.create(req);
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus() == PENDING_PAYMENT);
    }

}
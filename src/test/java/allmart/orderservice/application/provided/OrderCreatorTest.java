package allmart.orderservice.application.provided;

import allmart.orderservice.domain.order.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static allmart.orderservice.domain.order.OrderStatus.PENDING_PAYMENT;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(ExternalClientTestConfig.class)
record OrderCreatorTest(OrderCreator orderCreator, EntityManager entityManager) {

    @BeforeEach
    void setUp() {
        entityManager.persist(MartDeliveryConfig.create(1L, Money.of(3_000L), Money.of(50_000L)));
        entityManager.flush();
    }

    @Test
    void createOrder() {
        OrderCreateRequest req = new OrderCreateRequest(
                23L,
                OrderPayMethod.CARD,
                List.of(new OrderLine(1L, "사과", new Money(1000), 2, "TAXABLE")),
                new DeliverySnapshot("47352", "부산광역시 부산진구", "범내골역 4번 출구"),
                new MartSnapshot(1L, "부산 범내골 마트", null),
                null
        );

        Order order = orderCreator.create(req);
        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(PENDING_PAYMENT);
        assertThat(order.getPayMethod()).isEqualTo(OrderPayMethod.CARD);
        assertThat(order.getMartSnapshot().martName()).isEqualTo("부산 범내골 마트");
    }
}

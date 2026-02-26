package allmart.orderservice.application.provided;

import allmart.orderservice.domain.order.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
record OrderFinderTest(OrderFinder orderFinder, OrderCreator orderCreator, EntityManager entityManager) {


    @Test
    void findOrder() {
        OrderCreateRequest req = new OrderCreateRequest(23L,
                List.of(new OrderLine(1L, "사과", new Money(1000), 2)),
                new ShippingInfo("김아무개", "01012345678", new Address("47352", "부산광역시", "범내골역4번출구"), "잘 부탁드려요"));

        Order order = orderCreator.create(req);

        entityManager.flush();
        entityManager.clear();

        Order found = orderFinder.findDetailById(order.getId());

        assertThat(order.getId()).isEqualTo(found.getId());
    }
}
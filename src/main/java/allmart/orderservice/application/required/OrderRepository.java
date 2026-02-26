package allmart.orderservice.application.required;

import allmart.orderservice.domain.order.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 주문 정보를 저장하거나 조회한다
 */
public interface OrderRepository extends Repository<Order, Long> {
    Order save(Order order);

    Optional<Order> findByTossOrderId(String tossOrderId);

    @Query("""
    select distinct o
    from Order o
    left join fetch o.orderLines
    where o.id = :orderId
""")
    Optional<Order> findDetailById(@Param("orderId")Long orderId);
}

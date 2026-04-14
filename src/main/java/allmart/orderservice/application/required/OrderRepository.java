package allmart.orderservice.application.required;

import allmart.orderservice.domain.order.Order;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 주문 정보를 저장하거나 조회한다
 */
public interface OrderRepository extends Repository<Order, Long> {
    Order save(Order order);

    /** tossOrderId로 주문 단순 조회 (Kafka 결제 결과 처리 시 사용) */
    Optional<Order> findByTossOrderId(String tossOrderId);

    /** orderId로 orderLines fetch join 조회 — N+1 방지 */
    @Query("""
          select distinct o
          from Order o
          left join fetch o.orderLines
          where o.id = :orderId
          """)
    Optional<Order> findDetailById(@Param("orderId")Long orderId);

}

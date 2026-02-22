package allmart.orderservice.application.required;

import allmart.orderservice.domain.order.Order;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * 주문 정보를 저장하거나 조회한다
 */
public interface OrderRepository extends Repository<Order, Long> {
    Order save(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByTossOrderId(String tossOrderId);
}

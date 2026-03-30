package allmart.orderservice.application.provided;

import allmart.orderservice.domain.order.Order;

import java.util.List;

public interface OrderFinder {
    Order findDetailById(Long orderId);

    Order findByTossOrderId(String tossOrderId);

    List<Order> findAll();
}

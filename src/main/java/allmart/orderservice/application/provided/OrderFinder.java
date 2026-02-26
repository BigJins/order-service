package allmart.orderservice.application.provided;

import allmart.orderservice.domain.order.Order;

public interface OrderFinder {
    Order findDetailById(Long orderId);

    Order findByTossOrderId(String tossOrderId);
}

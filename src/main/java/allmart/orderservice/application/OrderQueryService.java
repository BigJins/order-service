package allmart.orderservice.application;

import allmart.orderservice.application.provided.OrderFinder;
import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.domain.order.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Transactional(readOnly = true)
@Validated
@RequiredArgsConstructor
public class OrderQueryService implements OrderFinder {
    private final OrderRepository orderRepository;

    @Override
    public Order find(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new RuntimeException("Order not found"));
    }

    @Override
    public Order findByTossOrderId(String tossOrderId) {
        return orderRepository.findByTossOrderId(tossOrderId).orElseThrow(() -> new RuntimeException("TossOrderId not found"));
    }
}

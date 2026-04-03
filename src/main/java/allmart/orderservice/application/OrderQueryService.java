package allmart.orderservice.application;

import allmart.orderservice.application.provided.OrderFinder;
import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderNotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.List;
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
    public Order findDetailById(Long orderId) {
        return orderRepository.findDetailById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    public Order findByTossOrderId(String tossOrderId) {
        return orderRepository.findByTossOrderId(tossOrderId).orElseThrow(() -> new OrderNotFoundException(tossOrderId));
    }

    @Override
    public List<Order> findAll() {
        return orderRepository.findAllWithLines();
    }
}

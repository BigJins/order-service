package allmart.orderservice.application;

import allmart.orderservice.application.provided.OrderCreator;
import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Log4j2
@Service
@Transactional
@Validated
@RequiredArgsConstructor
public class OrderModifyService implements OrderCreator {

    private final OrderRepository orderRepository;

    @Override
    public Order create(OrderCreateRequest orderCreateRequest) {

        Order order = Order.create(orderCreateRequest);

        return orderRepository.save(order);
    }

    @Override
    public void applyPaid(String tossOrderId, String paymentKey, long amount) {

        Order order = orderRepository.findByTossOrderId(tossOrderId).orElse(null);
        if (order == null) {
            log.warn("Kafka 메시지 무시 — 존재하지 않는 tossOrderId: {}", tossOrderId);
            return;
        }

        order.markAsPaid(amount);
    }

    @Override
    public void applyPaymentFailed(String tossOrderId, String paymentKey, long amount) {

        Order order = orderRepository.findByTossOrderId(tossOrderId).orElse(null);
        if (order == null) {
            log.warn("Kafka 메시지 무시 — 존재하지 않는 tossOrderId: {}", tossOrderId);
            return;
        }

        order.markPaymentFailed();
    }
}

package allmart.orderservice.application.query;

import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 Query 서비스.
 * readOnly=true → ReplicationRoutingDataSource가 Replica로 라우팅.
 * 테스트/로컬(레플리카 미설정)에서는 Primary(H2/MySQL)로 fallback.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderQueryService implements OrderQueryUseCase {

    private final OrderRepository orderRepository;

    @Override
    public Order findById(Long orderId) {
        return orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    public Page<Order> findByBuyer(Long buyerId, Pageable pageable) {
        return orderRepository.findPageByBuyerIdOrderByCreatedAtDesc(buyerId, pageable);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return orderRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}

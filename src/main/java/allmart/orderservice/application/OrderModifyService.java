package allmart.orderservice.application;

import allmart.orderservice.application.provided.OrderCreator;
import allmart.orderservice.application.provided.OrderFinder;
import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import allmart.orderservice.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Transactional
@Validated
@RequiredArgsConstructor
public class OrderModifyService implements OrderCreator {

    private final OrderRepository orderRepository;
    private final OrderFinder orderFinder;

    @Override
    public Order create(OrderCreateRequest orderCreateRequest) {

        Order order = Order.create(orderCreateRequest);
        orderRepository.save(order);

        return order;
    }

    @Override
    public void applyPaid(String tossOrderId, String paymentKey, long amount) {

        Order order = orderFinder.findByTossOrderId(tossOrderId);

        // 멱등 처리: 같은 메시지 재수신/재처리 대비
        if (order.getStatus() == OrderStatus.PAID) {
            return;
        }

        // (선택) 금액 검증까지 하고 싶으면 여기서:
        // if (order.getTotalAmount().amount() != amount) throw new IllegalStateException("결제 금액 불일치");

        order.markAsPaid();

        // 같은 트랜잭션 내에서 조회한 JPA 엔티티면 save 없어도 flush됨.
        // 그래도 명시적으로 하고 싶으면 아래 주석 해제:
        // orderRepository.save(order);
    }

    @Override
    public void applyPaymentFailed(String tossOrderId, String paymentKey, long amount) {
        Order order = orderFinder.findByTossOrderId(tossOrderId);

        // 멱등(최소): 이미 PAID면 실패로 덮어쓰면 안 됨
        if (order.getStatus() == OrderStatus.PAID) return;

        // 이미 실패 처리된 경우도 무시
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) return;

        order.markPaymentFailed();
    }
}

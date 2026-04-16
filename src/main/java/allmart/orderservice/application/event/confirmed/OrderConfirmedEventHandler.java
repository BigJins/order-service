package allmart.orderservice.application.event.confirmed;

import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 확정 처리 핸들러.
 * Zero Payload: orderId로 DB 재조회 후 order.confirmed.v1 Outbox 저장.
 * Debezium CDC → MongoDB 상태 갱신.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmedEventHandler {

    private final OrderRepository orderRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — Order 재조회 후 order.confirmed.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderConfirmedEvent event) {
        orderRepository.findDetailById(event.orderId()).ifPresentOrElse(
                order -> outboxEventPublisher.publishOrderConfirmed(order),
                () -> log.error("OrderConfirmedEventHandler: orderId={}를 찾을 수 없음", event.orderId())
        );
    }
}

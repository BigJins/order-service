package allmart.orderservice.application.event.confirmed;

import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 확정 처리 핸들러.
 * order.confirmed.v1 Outbox 저장 → Debezium CDC → order-query-service MongoDB 상태 갱신.
 */
@Component
@RequiredArgsConstructor
public class OrderConfirmedEventHandler {

    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — order.confirmed.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderConfirmedEvent event) {
        outboxEventPublisher.publishOrderConfirmed(event.order());
    }
}

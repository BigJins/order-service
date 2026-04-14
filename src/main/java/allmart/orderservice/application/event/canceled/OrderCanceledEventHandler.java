package allmart.orderservice.application.event.canceled;

import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 취소 처리 핸들러.
 * order.canceled.v1 Outbox 저장 → Debezium CDC → inventory-service 재고 해제 (비동기).
 */
@Component
@RequiredArgsConstructor
public class OrderCanceledEventHandler {

    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — order.canceled.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderCanceledEvent event) {
        outboxEventPublisher.publishOrderCanceled(event.order());
    }
}

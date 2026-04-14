package allmart.orderservice.application.event.failed;

import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 실패 처리 핸들러.
 * order.failed.v1 Outbox 저장 → Debezium CDC → MongoDB 상태 갱신.
 * 재고 해제는 inventory-service가 payment.result.v1 FAILED를 직접 소비하여 처리한다.
 */
@Component
@RequiredArgsConstructor
public class OrderPaymentFailedEventHandler {

    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — order.failed.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderPaymentFailedEvent event) {
        outboxEventPublisher.publishOrderFailed(event.order());
    }
}

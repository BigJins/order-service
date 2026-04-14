package allmart.orderservice.application.event.paid;

import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 완료 처리 핸들러.
 * order.paid.v1 Outbox 저장 → Debezium CDC → delivery-service 배송 생성 트리거.
 * 재고 확정은 inventory-service가 order.paid.v1을 직접 소비하여 처리한다.
 */
@Component
@RequiredArgsConstructor
public class OrderPaidEventHandler {

    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — order.paid.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderPaidEvent event) {
        outboxEventPublisher.publishOrderPaid(event.order());
    }
}

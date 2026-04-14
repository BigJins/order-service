package allmart.orderservice.application.event.created;

import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 생성 시 공통 처리 — 결제수단 무관하게 항상 실행.
 * order.created.v1 Outbox 저장 → Debezium CDC → order-query-service MongoDB 초기 도큐먼트 생성.
 */
@Component
@RequiredArgsConstructor
public class OrderCreatedEventHandler {

    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — 주문 저장 트랜잭션 내에서 order.created.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderCreatedEvent event) {
        outboxEventPublisher.publishOrderCreated(event.order());
    }
}

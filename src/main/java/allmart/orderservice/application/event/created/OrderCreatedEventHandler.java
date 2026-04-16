package allmart.orderservice.application.event.created;

import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 생성 시 공통 처리 — 결제수단 무관하게 항상 실행.
 * Zero Payload: orderId로 DB 재조회 후 order.created.v1 Outbox 저장.
 * Debezium CDC → inventory-service 재고 비동기 예약 + order-query-service MongoDB 초기 생성.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedEventHandler {

    private final OrderRepository orderRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — 주문 저장 트랜잭션 내에서 Order 재조회 후 order.created.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderCreatedEvent event) {
        orderRepository.findDetailById(event.orderId()).ifPresentOrElse(
                order -> outboxEventPublisher.publishOrderCreated(order),
                () -> log.error("OrderCreatedEventHandler: orderId={}를 찾을 수 없음 — Outbox 저장 실패", event.orderId())
        );
    }
}

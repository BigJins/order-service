package allmart.orderservice.application.event.paid;

import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 완료 처리 핸들러.
 * Zero Payload: orderId로 DB 재조회 후 order.paid.v1 Outbox 저장.
 * Debezium CDC → delivery-service 배송 생성 + inventory-service 재고 확정.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventHandler {

    private final OrderRepository orderRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — Order 재조회 후 order.paid.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderPaidEvent event) {
        orderRepository.findDetailById(event.orderId()).ifPresentOrElse(
                order -> outboxEventPublisher.publishOrderPaid(order),
                () -> log.error("OrderPaidEventHandler: orderId={}를 찾을 수 없음 — Outbox 저장 실패", event.orderId())
        );
    }
}

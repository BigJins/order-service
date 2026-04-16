package allmart.orderservice.application.event.canceled;

import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 취소 처리 핸들러.
 * Zero Payload: orderId로 DB 재조회 후 order.canceled.v1 Outbox 저장.
 * Debezium CDC → inventory-service 재고 해제 + pay-service 환불(예정).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCanceledEventHandler {

    private final OrderRepository orderRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — Order 재조회 후 order.canceled.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderCanceledEvent event) {
        orderRepository.findDetailById(event.orderId()).ifPresentOrElse(
                order -> outboxEventPublisher.publishOrderCanceled(order),
                () -> log.error("OrderCanceledEventHandler: orderId={}를 찾을 수 없음", event.orderId())
        );
    }
}

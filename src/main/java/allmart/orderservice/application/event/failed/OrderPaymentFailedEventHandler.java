package allmart.orderservice.application.event.failed;

import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 실패 처리 핸들러.
 * Zero Payload: orderId로 DB 재조회 후 order.failed.v1 Outbox 저장.
 * 재고 해제는 inventory-service가 payment.result.v1 FAILED를 직접 소비하여 처리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentFailedEventHandler {

    private final OrderRepository orderRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — Order 재조회 후 order.failed.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderPaymentFailedEvent event) {
        orderRepository.findDetailById(event.orderId()).ifPresentOrElse(
                order -> outboxEventPublisher.publishOrderFailed(order),
                () -> log.error("OrderPaymentFailedEventHandler: orderId={}를 찾을 수 없음", event.orderId())
        );
    }
}

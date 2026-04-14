package allmart.orderservice.application.event.created;

import allmart.orderservice.application.required.OutboxEventPublisher;
import allmart.orderservice.domain.order.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 후불 결제(CASH_ON_DELIVERY, CARD_ON_DELIVERY) 공통 핸들러.
 * pay-service를 거치지 않으므로 주문 생성 시점에 즉시 배송 트리거.
 * order.paid.v1 Outbox 저장 → Debezium CDC → delivery-service 배송 생성.
 * 재고 확정은 inventory-service가 order.paid.v1을 직접 소비하여 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnDeliveryPaymentEventHandler {

    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — 후불 결제 수단에 한해 order.paid.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderCreatedEvent event) {
        Order order = event.order();
        if (!order.getPayMethod().isOnDeliveryPayment()) return; // 온라인 결제 수단은 무시

        outboxEventPublisher.publishOrderPaid(order);
        log.info("후불 결제({}) — 배송 즉시 트리거: orderId={}, tossOrderId={}",
                order.getPayMethod(), order.getId(), order.getTossOrderId());
    }
}

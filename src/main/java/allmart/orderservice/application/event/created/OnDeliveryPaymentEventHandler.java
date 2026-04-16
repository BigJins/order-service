package allmart.orderservice.application.event.created;

import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.application.required.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 후불 결제(CASH_ON_DELIVERY, CARD_ON_DELIVERY, CART_ON_DELIVERY) 공통 핸들러.
 * Zero Payload: payMethod로 후불 여부 판단 후 orderId로 DB 재조회.
 * pay-service를 거치지 않으므로 주문 생성 시점에 즉시 배송 트리거.
 * order.paid.v1 Outbox 저장 → Debezium CDC → delivery-service 배송 생성.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnDeliveryPaymentEventHandler {

    private final OrderRepository orderRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /** BEFORE_COMMIT — 후불 결제 수단에 한해 Order 재조회 후 order.paid.v1 Outbox 저장 */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(OrderCreatedEvent event) {
        if (!event.payMethod().isOnDeliveryPayment()) return; // 온라인 결제 수단은 무시

        orderRepository.findDetailById(event.orderId()).ifPresentOrElse(
                order -> {
                    outboxEventPublisher.publishOrderPaid(order);
                    log.info("후불 결제({}) — 배송 즉시 트리거: orderId={}, tossOrderId={}",
                            event.payMethod(), order.getId(), order.getTossOrderId());
                },
                () -> log.error("OnDeliveryPaymentEventHandler: orderId={}를 찾을 수 없음", event.orderId())
        );
    }
}

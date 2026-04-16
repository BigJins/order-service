package allmart.orderservice.application.event.sync;

import allmart.orderservice.application.event.canceled.OrderCanceledEvent;
import allmart.orderservice.application.event.confirmed.OrderConfirmedEvent;
import allmart.orderservice.application.event.created.OrderCreatedEvent;
import allmart.orderservice.application.event.failed.OrderPaymentFailedEvent;
import allmart.orderservice.application.event.paid.OrderPaidEvent;
import allmart.orderservice.application.required.OrderDocumentRepository;
import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.domain.order.document.OrderDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

/**
 * MySQL 커밋 완료 후 MongoDB 읽기 모델 동기화 — AFTER_COMMIT.
 * BEFORE_COMMIT Outbox 핸들러와 독립적으로 동작.
 * 실패 시 로그만 남김 — MongoDB 미동기화는 Command 성공에 영향 없음.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDocumentSyncHandler {

    private final OrderRepository orderRepository;
    private final OrderDocumentRepository documentRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        orderRepository.findDetailById(event.orderId()).ifPresentOrElse(
                order -> documentRepository.save(OrderDocument.from(order)),
                () -> log.warn("OrderDocumentSync: 주문 생성 동기화 실패 — orderId={} 조회 불가", event.orderId())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        LocalDateTime now = LocalDateTime.now();
        documentRepository.findById(event.orderId()).ifPresentOrElse(
                doc -> {
                    doc.applyPaid(now);
                    documentRepository.save(doc);
                },
                () -> log.warn("OrderDocumentSync: PAID 동기화 실패 — orderId={} 도큐먼트 없음", event.orderId())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaymentFailed(OrderPaymentFailedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        documentRepository.findById(event.orderId()).ifPresentOrElse(
                doc -> {
                    doc.applyFailed(now);
                    documentRepository.save(doc);
                },
                () -> log.warn("OrderDocumentSync: PAYMENT_FAILED 동기화 실패 — orderId={} 도큐먼트 없음", event.orderId())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        documentRepository.findById(event.orderId()).ifPresentOrElse(
                doc -> {
                    doc.applyConfirmed(now);
                    documentRepository.save(doc);
                },
                () -> log.warn("OrderDocumentSync: CONFIRMED 동기화 실패 — orderId={} 도큐먼트 없음", event.orderId())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCanceled(OrderCanceledEvent event) {
        LocalDateTime now = LocalDateTime.now();
        documentRepository.findById(event.orderId()).ifPresentOrElse(
                doc -> {
                    doc.applyCanceled(now);
                    documentRepository.save(doc);
                },
                () -> log.warn("OrderDocumentSync: CANCELED 동기화 실패 — orderId={} 도큐먼트 없음", event.orderId())
        );
    }
}

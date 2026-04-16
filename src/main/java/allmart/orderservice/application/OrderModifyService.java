package allmart.orderservice.application;

import allmart.orderservice.application.command.OrderCommandUseCase;
import allmart.orderservice.application.event.canceled.OrderCanceledEvent;
import allmart.orderservice.application.event.confirmed.OrderConfirmedEvent;
import allmart.orderservice.application.event.created.OrderCreatedEvent;
import allmart.orderservice.application.event.failed.OrderPaymentFailedEvent;
import allmart.orderservice.application.event.paid.OrderPaidEvent;
import allmart.orderservice.application.required.MartDeliveryConfigRepository;
import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.application.required.ProductPort;
import allmart.orderservice.domain.order.MartDeliveryConfig;
import allmart.orderservice.domain.order.Money;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import allmart.orderservice.domain.order.OrderLine;
import allmart.orderservice.domain.order.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Optional;

/**
 * 주문 Command 유스케이스 구현체 — OrderCommandUseCase.
 * Zero Payload 도메인 이벤트를 발행하고, @TransactionalEventListener 핸들러가
 * DB에서 Order를 재조회하여 Outbox 저장과 외부 이벤트 발행을 담당한다.
 */
@Slf4j
@Service
@Transactional
@Validated
@RequiredArgsConstructor
public class OrderModifyService implements OrderCommandUseCase {

    private final OrderRepository orderRepository;
    private final MartDeliveryConfigRepository martDeliveryConfigRepository;
    private final ProductPort productPort;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 주문 생성 — 배달 설정 조회 → 가격 검증 → Order 저장 → OrderCreatedEvent 발행.
     * 재고 예약은 inventory-service가 order.created.v1을 소비하여 비동기 처리.
     * Outbox 저장은 @TransactionalEventListener 핸들러가 담당.
     */
    @Override
    public Order create(OrderCreateRequest req) {
        MartDeliveryConfig deliveryConfig = martDeliveryConfigRepository.findByMartId(req.martSnapshot().martId())
                .orElseThrow(() -> new IllegalArgumentException("마트 배달 설정이 등록되지 않았습니다. martId=" + req.martSnapshot().martId()));

        List<OrderLine> enrichedLines = enrichAndValidatePrices(req.orderLines());
        OrderCreateRequest enrichedRequest = new OrderCreateRequest(
                req.buyerId(), req.payMethod(), enrichedLines,
                req.deliverySnapshot(), req.martSnapshot(), req.orderMemo());

        Order order = Order.create(enrichedRequest, deliveryConfig);
        Order saved = orderRepository.save(order);
        log.info("주문 생성 완료: orderId={}, tossOrderId={}, buyerId={}, payMethod={}, totalAmount={}, status={}",
                saved.getId(), saved.getTossOrderId(), saved.getBuyerId(),
                saved.getPayMethod(), saved.getTotalAmount().amount(), saved.getStatus());

        // Zero Payload — orderId + payMethod만 전달
        eventPublisher.publishEvent(new OrderCreatedEvent(saved.getId(), saved.getPayMethod()));
        return saved;
    }

    /** payment.result.v1 DONE 처리 — 주문 PAID 전이 후 OrderPaidEvent 발행 */
    @Override
    public void applyPaid(String tossOrderId, String paymentKey, Money amount) {
        findByTossOrderId(tossOrderId).ifPresent(order -> {
            order.markAsPaid(amount);
            eventPublisher.publishEvent(new OrderPaidEvent(order.getId()));
            log.info("결제 완료: tossOrderId={}, orderId={}, buyerId={}, amount={}", tossOrderId, order.getId(), order.getBuyerId(), amount);
        });
    }

    /** payment.result.v1 FAILED 처리 — 주문 PAYMENT_FAILED 전이 후 OrderPaymentFailedEvent 발행 */
    @Override
    public void applyPaymentFailed(String tossOrderId, String paymentKey, Money amount) {
        findByTossOrderId(tossOrderId).ifPresent(order -> {
            order.markPaymentFailed();
            eventPublisher.publishEvent(new OrderPaymentFailedEvent(order.getId()));
            log.warn("결제 실패: tossOrderId={}, orderId={}, buyerId={}", tossOrderId, order.getId(), order.getBuyerId());
        });
    }

    /** delivery.completed.v1 처리 — 주문 CONFIRMED 전이 후 OrderConfirmedEvent 발행 */
    @Override
    public void applyDeliveryCompleted(Long orderId) {
        orderRepository.findDetailById(orderId).ifPresentOrElse(
                order -> {
                    order.markAsCompleted();
                    eventPublisher.publishEvent(new OrderConfirmedEvent(order.getId()));
                    log.info("배달 완료 → 주문 CONFIRMED: orderId={}, buyerId={}", orderId, order.getBuyerId());
                },
                () -> log.warn("Kafka 메시지 무시 — 존재하지 않는 orderId: {}", orderId)
        );
    }

    /** 재결제 요청 — PAYMENT_FAILED → PENDING_PAYMENT, tossOrderId 재발급 */
    @Override
    public void retryPayment(Long orderId, Long buyerId) {
        Order order = orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        String oldTossOrderId = order.getTossOrderId();
        order.retryPayment(buyerId);
        log.info("재결제 요청 → PENDING_PAYMENT: orderId={}, buyerId={}, 구 tossOrderId={}, 신 tossOrderId={}",
                orderId, buyerId, oldTossOrderId, order.getTossOrderId());
    }

    /** 주문 취소 — PENDING_PAYMENT → CANCELED, OrderCanceledEvent 발행 */
    @Override
    public void cancelOrder(Long orderId, Long buyerId) {
        Order order = orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.cancel(buyerId);
        eventPublisher.publishEvent(new OrderCanceledEvent(order.getId()));
        log.info("주문 취소: orderId={}, buyerId={}, tossOrderId={}", orderId, buyerId, order.getTossOrderId());
    }

    /** 재고 부족 시 시스템 자동 취소 — buyerId 검증 없음, OrderCanceledEvent 발행 */
    @Override
    public void cancelByReserveFailed(String tossOrderId) {
        findByTossOrderId(tossOrderId).ifPresent(order -> {
            order.cancelBySystem();
            eventPublisher.publishEvent(new OrderCanceledEvent(order.getId()));
            log.warn("재고 부족으로 주문 자동 취소: tossOrderId={}, orderId={}", tossOrderId, order.getId());
        });
    }

    /** tossOrderId로 주문 조회. 없으면 WARN 로그 후 empty 반환 — Kafka skip 패턴 */
    private Optional<Order> findByTossOrderId(String tossOrderId) {
        var opt = orderRepository.findByTossOrderId(tossOrderId);
        if (opt.isEmpty()) log.warn("Kafka 메시지 무시 — 존재하지 않는 tossOrderId: {}", tossOrderId);
        return opt;
    }

    /**
     * 가격 검증 + taxType 서버 주입.
     * 클라이언트가 보낸 unitPrice를 product-service 현재 가격과 대조 후
     * taxType을 서버에서 채워 새 OrderLine 리스트를 반환.
     */
    private List<OrderLine> enrichAndValidatePrices(List<OrderLine> orderLines) {
        return orderLines.stream()
                .map(line -> {
                    ProductPort.ProductInfo info = productPort.getProductInfo(line.productId());
                    if (!info.price().equals(line.unitPrice())) {
                        throw new IllegalArgumentException(
                                "가격이 변경된 상품이 있습니다. productId=" + line.productId()
                                + " (요청=" + line.unitPrice().amount()
                                + ", 현재=" + info.price().amount() + ")");
                    }
                    return new OrderLine(line.productId(), line.productNameSnapshot(),
                            line.unitPrice(), line.quantity(), info.taxType());
                })
                .toList();
    }
}

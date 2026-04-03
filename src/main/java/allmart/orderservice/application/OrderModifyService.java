package allmart.orderservice.application;

import allmart.orderservice.adapter.client.InventoryServiceClient;
import allmart.orderservice.adapter.client.ProductServiceClient;
import allmart.orderservice.adapter.client.dto.InventoryReserveRequest;
import allmart.orderservice.adapter.client.dto.ProductPriceResponse;
import allmart.orderservice.application.provided.OrderCreator;
import allmart.orderservice.application.required.OrderRepository;
import allmart.orderservice.application.required.OutboxEventPublisher;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import allmart.orderservice.domain.order.OrderLine;
import allmart.orderservice.domain.order.OrderPayMethod;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Log4j2
@Service
@Transactional
@Validated
@RequiredArgsConstructor
public class OrderModifyService implements OrderCreator {

    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;
    private final InventoryServiceClient inventoryServiceClient;
    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * 주문 생성 흐름:
     * 1. Order 도메인 객체 생성 (tossOrderId 생성, DB 저장 전)
     * 2. product-service: 각 상품의 현재 가격 검증
     * 3. inventory-service: 재고 예약 (실패 시 주문 생성 중단)
     * 4. OrderRepository 저장 (orderId 확정)
     */
    @Override
    public Order create(OrderCreateRequest orderCreateRequest) {
        Order order = Order.create(orderCreateRequest);

        validatePrices(order);
        reserveInventory(order);

        Order saved = orderRepository.save(order);
        log.info("주문 생성: orderId={}, tossOrderId={}, buyerId={}, payMethod={}", saved.getId(), saved.getTossOrderId(), saved.getBuyerId(), saved.getPayMethod());

        // 후불 현금은 pay-service를 거치지 않으므로 주문 생성 시점에 즉시 배송 트리거
        if (saved.getPayMethod() == OrderPayMethod.CASH_ON_DELIVERY) {
            outboxEventPublisher.publishOrderPaid(saved);
            confirmInventory(saved.getTossOrderId());
            log.info("후불 현금 — 배송 즉시 트리거: orderId={}", saved.getId());
        }

        return saved;
    }

    @Override
    public void applyPaid(String tossOrderId, String paymentKey, long amount) {
        Order order = orderRepository.findByTossOrderId(tossOrderId).orElse(null);
        if (order == null) {
            log.warn("Kafka 메시지 무시 — 존재하지 않는 tossOrderId: {}", tossOrderId);
            return;
        }

        order.markAsPaid(amount);
        outboxEventPublisher.publishOrderPaid(order);  // Outbox: order.paid.v1 저장 (같은 트랜잭션)
        confirmInventory(tossOrderId);
        log.info("결제 완료 처리: tossOrderId={}, orderId={}", tossOrderId, order.getId());
    }

    @Override
    public void applyPaymentFailed(String tossOrderId, String paymentKey, long amount) {
        Order order = orderRepository.findByTossOrderId(tossOrderId).orElse(null);
        if (order == null) {
            log.warn("Kafka 메시지 무시 — 존재하지 않는 tossOrderId: {}", tossOrderId);
            return;
        }

        order.markPaymentFailed();
        releaseInventory(tossOrderId);
        log.info("결제 실패 처리: tossOrderId={}, orderId={}", tossOrderId, order.getId());
    }

    @Override
    public void applyDeliveryCompleted(Long orderId) {
        Order order = orderRepository.findDetailById(orderId).orElse(null);
        if (order == null) {
            log.warn("Kafka 메시지 무시 — 존재하지 않는 orderId: {}", orderId);
            return;
        }
        order.markAsCompleted();
        log.info("배달 완료 처리: orderId={}", orderId);
    }

    @Override
    public void confirmCashPayment(Long orderId) {
        Order order = orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));
        order.confirmCashPayment();
        outboxEventPublisher.publishOrderPaid(order); // 배송 생성 트리거
        confirmInventory(order.getTossOrderId());     // 재고 확정 (RESERVED → DEDUCTED)
        log.info("현금 선불 확인: orderId={}", orderId);
    }

    @Override
    public void retryPayment(Long orderId) {
        Order order = orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));
        order.retryPayment();
        log.info("재결제 요청: orderId={}, 신규 tossOrderId={}", orderId, order.getTossOrderId());
    }

    private void validatePrices(Order order) {
        for (OrderLine line : order.getOrderLines()) {
            ProductPriceResponse priceResponse = productServiceClient.getPrice(line.productId());
            if (priceResponse.price() != line.unitPrice().amount()) {
                throw new IllegalArgumentException(
                        "가격이 변경된 상품이 있습니다. productId=" + line.productId()
                        + " (요청=" + line.unitPrice().amount()
                        + ", 현재=" + priceResponse.price() + ")"
                );
            }
        }
    }

    private void reserveInventory(Order order) {
        List<InventoryReserveRequest.ReserveItem> items = order.getOrderLines().stream()
                .map(line -> new InventoryReserveRequest.ReserveItem(line.productId(), line.quantity()))
                .toList();

        inventoryServiceClient.reserve(new InventoryReserveRequest(order.getTossOrderId(), items));
    }

    private void confirmInventory(String tossOrderId) {
        try {
            inventoryServiceClient.confirm(tossOrderId);
        } catch (Exception e) {
            // 재고 확정 실패는 주문 상태 전이를 막지 않음 (별도 알람/재처리 필요)
            log.error("재고 확정 실패 — tossOrderId={}, error={}", tossOrderId, e.getMessage());
        }
    }

    private void releaseInventory(String tossOrderId) {
        try {
            inventoryServiceClient.release(tossOrderId);
        } catch (Exception e) {
            // 재고 해제 실패는 주문 상태 전이를 막지 않음 (별도 알람/재처리 필요)
            log.error("재고 해제 실패 — tossOrderId={}, error={}", tossOrderId, e.getMessage());
        }
    }
}

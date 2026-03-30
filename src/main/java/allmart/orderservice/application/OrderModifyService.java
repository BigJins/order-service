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

        return orderRepository.save(order);
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
    }

    @Override
    public void applyDeliveryCompleted(Long orderId) {
        Order order = orderRepository.findDetailById(orderId).orElse(null);
        if (order == null) {
            log.warn("Kafka 메시지 무시 — 존재하지 않는 orderId: {}", orderId);
            return;
        }
        order.markAsCompleted();
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

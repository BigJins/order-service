package allmart.orderservice.adapter.kafka.dto;

import allmart.orderservice.domain.order.Order;

import java.time.LocalDateTime;
import java.util.List;

/**
 * order.created.v1 Kafka 이벤트 페이로드
 * order-query-service(MongoDB)가 이 이벤트를 소비하여 OrderDocument를 초기 생성함.
 * 수령인명/전화번호 미포함 — 개인정보 보호.
 */
public record OrderCreatedPayload(
        String orderId,
        String buyerId,
        String tossOrderId,
        String status,
        String payMethod,
        long totalAmount,
        List<OrderLineDto> orderLines,
        List<ChargeLineDto> chargeLines,
        DeliverySnapshotDto deliverySnapshot,
        MartSnapshotDto martSnapshot,
        OrderMemoDto orderMemo,
        List<StatusHistoryDto> statusHistory,  // 초기 상태 이력 (append-only 시작점)
        LocalDateTime createdAt
) {
    public OrderCreatedPayload {
        orderLines    = List.copyOf(orderLines);
        chargeLines   = List.copyOf(chargeLines);
        statusHistory = List.copyOf(statusHistory);
    }

    public record OrderLineDto(String productId, String productNameSnapshot, long unitPrice, int quantity) {}
    public record ChargeLineDto(String type, long amount) {}
    public record DeliverySnapshotDto(String zipCode, String roadAddress, String detailAddress) {}
    public record MartSnapshotDto(String martId, String martName, String martPhone) {}
    public record OrderMemoDto(String orderRequest, String deliveryRequest) {}
    public record StatusHistoryDto(String status, LocalDateTime at) {}

    public static OrderCreatedPayload from(Order order) {
        var ds = order.getDeliverySnapshot();
        var ms = order.getMartSnapshot();
        var memo = order.getOrderMemo();

        var lines = order.getOrderLines().stream()
                .map(l -> new OrderLineDto(
                        String.valueOf(l.productId()),
                        l.productNameSnapshot(),
                        l.unitPrice().amount(),
                        l.quantity()
                ))
                .toList();

        var charges = order.getChargeLines().stream()
                .map(cl -> new ChargeLineDto(cl.type().name(), cl.amount().amount()))
                .toList();

        var initialHistory = List.of(new StatusHistoryDto(order.getStatus().name(), order.getCreatedAt()));

        return new OrderCreatedPayload(
                String.valueOf(order.getId()),
                String.valueOf(order.getBuyerId()),
                order.getTossOrderId(),
                order.getStatus().name(),
                order.getPayMethod().name(),
                order.getTotalAmount().amount(),
                lines,
                charges,
                new DeliverySnapshotDto(ds.zipCode(), ds.roadAddress(), ds.detailAddress()),
                new MartSnapshotDto(String.valueOf(ms.martId()), ms.martName(), ms.martPhone()),
                memo != null ? new OrderMemoDto(memo.orderRequest(), memo.deliveryRequest()) : null,
                initialHistory,
                order.getCreatedAt()
        );
    }
}

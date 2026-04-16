package allmart.orderservice.adapter.kafka.dto;

import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderPayMethod;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * order.paid.v1 Kafka 이벤트 페이로드
 *
 * [delivery-service 호환] deliveryAddress, mart, orderLines.productName, paidAt
 * [MongoDB Sink 전체 상태] status, payMethod, chargeLines, deliverySnapshot,
 *                          martSnapshot, orderMemo, statusHistory, createdAt
 *
 * 수령인명/전화번호 미포함 — PII 보호.
 */
public record OrderPaidPayload(
        // ── delivery-service 호환 필드 ──────────────────────────────
        Long orderId,
        String tossOrderId,
        Long buyerId,
        long totalAmount,
        DeliveryAddressDto deliveryAddress,
        MartDto mart,
        List<OrderLineDto> orderLines,
        LocalDateTime paidAt,

        // ── MongoDB OrderDocument 전체 상태 필드 ────────────────────
        String status,
        String payMethod,
        List<ChargeLineDto> chargeLines,
        DeliverySnapshotDto deliverySnapshot,
        MartSnapshotDto martSnapshot,
        OrderMemoDto orderMemo,
        List<StatusHistoryDto> statusHistory,
        LocalDateTime createdAt
) {
    public OrderPaidPayload {
        orderLines    = List.copyOf(orderLines);
        chargeLines   = List.copyOf(chargeLines);
        statusHistory = List.copyOf(statusHistory);
    }

    public record DeliveryAddressDto(String zipCode, String roadAddress, String detailAddress) {}
    public record MartDto(Long martId, String martName) {}
    public record OrderLineDto(String productId, String productNameSnapshot, int quantity, long unitPrice) {}

    public record DeliverySnapshotDto(String zipCode, String roadAddress, String detailAddress) {}
    public record MartSnapshotDto(String martId, String martName, String martPhone) {}
    public record ChargeLineDto(String type, long amount) {}
    public record OrderMemoDto(String orderRequest, String deliveryRequest) {}
    public record StatusHistoryDto(String status, LocalDateTime at) {}

    public static OrderPaidPayload from(Order order) {
        var ds   = order.getDeliverySnapshot();
        var ms   = order.getMartSnapshot();
        var memo = order.getOrderMemo();

        var orderLines = order.getOrderLines().stream()
                .map(l -> new OrderLineDto(String.valueOf(l.productId()), l.productNameSnapshot(), l.quantity(), l.unitPrice().amount()))
                .toList();

        var chargeLines = order.getChargeLines().stream()
                .map(cl -> new ChargeLineDto(cl.type().name(), cl.amount().amount()))
                .toList();

        var initialStatus = order.getPayMethod().isOnDeliveryPayment() ? "PAID" : "PENDING_PAYMENT";
        var history = new ArrayList<StatusHistoryDto>();
        history.add(new StatusHistoryDto(initialStatus, order.getCreatedAt()));
        if (order.getPaidAt() != null && !"PAID".equals(initialStatus)) {
            history.add(new StatusHistoryDto("PAID", order.getPaidAt()));
        }

        return new OrderPaidPayload(
                order.getId(),
                order.getTossOrderId(),
                order.getBuyerId(),
                order.getTotalAmount().amount(),
                new DeliveryAddressDto(ds.zipCode(), ds.roadAddress(), ds.detailAddress()),
                new MartDto(ms.martId(), ms.martName()),
                orderLines,
                order.getPaidAt(),
                order.getStatus().name(),
                order.getPayMethod().name(),
                chargeLines,
                new DeliverySnapshotDto(ds.zipCode(), ds.roadAddress(), ds.detailAddress()),
                new MartSnapshotDto(String.valueOf(ms.martId()), ms.martName(), ms.martPhone()),
                memo != null ? new OrderMemoDto(memo.orderRequest(), memo.deliveryRequest()) : null,
                history,
                order.getCreatedAt()
        );
    }
}

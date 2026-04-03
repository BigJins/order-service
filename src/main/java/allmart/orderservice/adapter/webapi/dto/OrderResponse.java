package allmart.orderservice.adapter.webapi.dto;

import allmart.orderservice.domain.order.*;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        String tossOrderId,
        Long buyerId,
        OrderPayMethod payMethod,
        long totalAmount,
        OrderStatus status,
        LocalDateTime createdAt,
        DeliverySnapshot deliverySnapshot,
        MartSnapshot martSnapshot,
        OrderMemo orderMemo,
        List<OrderLineResponse> orderLines,
        List<ChargeLineResponse> chargeLines
) {
    public OrderResponse {
        orderLines = List.copyOf(orderLines);
        chargeLines = List.copyOf(chargeLines);
    }

    public record ChargeLineResponse(String type, long amount) {
        public static ChargeLineResponse of(ChargeLine cl) {
            return new ChargeLineResponse(cl.type().name(), cl.amount().amount());
        }
    }

    public static OrderResponse of(Order order) {
        List<OrderLineResponse> lines = order.getOrderLines().stream()
                .map(OrderLineResponse::of)
                .toList();
        List<ChargeLineResponse> charges = order.getChargeLines().stream()
                .map(ChargeLineResponse::of)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getTossOrderId(),
                order.getBuyerId(),
                order.getPayMethod(),
                order.getTotalAmount().amount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getDeliverySnapshot(),
                order.getMartSnapshot(),
                order.getOrderMemo(),
                lines,
                charges
        );
    }
}

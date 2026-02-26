package allmart.orderservice.adapter.wepapi.dto;

import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderStatus;
import allmart.orderservice.domain.order.ShippingInfo;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        String tossOrderId,
        Long buyerId,
        long amount,
        OrderStatus status,
        LocalDateTime createdAt,
        ShippingInfo shippingInfo,
        List<OrderLineResponse> orderLines
) {

    public OrderResponse {
        orderLines = List.copyOf(orderLines);
    }

    public static OrderResponse of(Order order) {
        List<OrderLineResponse> lines = order.getOrderLines().stream()
                .map(OrderLineResponse::of)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getTossOrderId(),
                order.getBuyerId(),
                order.getTotalAmount().amount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getShippingInfo(),
                lines
        );
    }
}
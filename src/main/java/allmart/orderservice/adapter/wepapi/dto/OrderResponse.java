package allmart.orderservice.adapter.wepapi.dto;

import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long orderId,
        String tossOrderId,
        Long buyerId,
        long amount,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public static OrderResponse of(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTossOrderId(),
                order.getBuyerId(),
                order.getTotalAmount().amount(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
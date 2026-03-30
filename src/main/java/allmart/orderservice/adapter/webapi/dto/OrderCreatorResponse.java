package allmart.orderservice.adapter.webapi.dto;

import allmart.orderservice.domain.order.Order;

public record OrderCreatorResponse (Long orderId, String tossOrderId, long amount) {

    public static OrderCreatorResponse of(Order order) {
        return new OrderCreatorResponse(
                order.getId(),
                order.getTossOrderId(),
                order.getTotalAmount().amount());
    }
}

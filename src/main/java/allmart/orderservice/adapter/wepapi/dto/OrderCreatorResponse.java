package allmart.orderservice.adapter.wepapi.dto;

import allmart.orderservice.domain.order.Order;

public record OrderCreatorResponse (String tossOrderId ,long amount) {

    public static OrderCreatorResponse of(Order order) {
        return new OrderCreatorResponse(
                order.getTossOrderId(),
                order.getTotalAmount().amount());
    }
}

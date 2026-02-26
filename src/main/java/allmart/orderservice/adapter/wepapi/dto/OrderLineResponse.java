package allmart.orderservice.adapter.wepapi.dto;

import allmart.orderservice.domain.order.OrderLine;

public record OrderLineResponse(
        Long productId,
        String productNameSnapshot,
        long unitPrice,
        int quantity,
        long lineAmount
) {
    static OrderLineResponse of(OrderLine ol) {
        return new OrderLineResponse(
                ol.productId(),
                ol.productNameSnapshot(),
                ol.unitPrice().amount(),
                ol.quantity(),
                ol.lineAmount().amount()
        );
    }
}
package allmart.orderservice.domain.order.document;

/** 주문 항목 — OrderDocument에 역정규화 embed */
public record OrderLineDoc(
        Long productId,
        String productNameSnapshot,
        long unitPrice,
        int quantity,
        long lineAmount
) {}

package allmart.orderservice.domain.order.document;

/** 주문/배달 요청사항 — OrderDocument에 역정규화 embed */
public record OrderMemoDoc(
        String orderRequest,
        String deliveryRequest
) {}

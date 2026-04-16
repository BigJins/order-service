package allmart.orderservice.adapter.webapi.dto;

import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderStatus;
import allmart.orderservice.domain.order.document.OrderDocument;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세 조회 응답 DTO.
 * MySQL Order(레거시) 또는 MongoDB OrderDocument 양쪽에서 생성 가능.
 */
public record OrderDetailResponse(
        Long orderId,
        String tossOrderId,
        Long buyerId,
        String payMethod,
        OrderStatus status,
        long totalAmount,
        String zipCode,
        String roadAddress,
        String detailAddress,
        Long martId,
        String martName,
        List<OrderLineResponse> orderLines,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime confirmedAt,
        LocalDateTime canceledAt
) {
    public OrderDetailResponse {
        orderLines = List.copyOf(orderLines);
    }

    /** MySQL Order → DTO (레거시 호환, 테스트에서 사용) */
    public static OrderDetailResponse from(Order order) {
        var ds = order.getDeliverySnapshot();
        var ms = order.getMartSnapshot();
        var lines = order.getOrderLines().stream()
                .map(OrderLineResponse::of)
                .toList();

        return new OrderDetailResponse(
                order.getId(),
                order.getTossOrderId(),
                order.getBuyerId(),
                order.getPayMethod().name(),
                order.getStatus(),
                order.getTotalAmount().amount(),
                ds.zipCode(),
                ds.roadAddress(),
                ds.detailAddress(),
                ms.martId(),
                ms.martName(),
                lines,
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getConfirmedAt(),
                order.getCanceledAt()
        );
    }

    /** MongoDB OrderDocument → DTO */
    public static OrderDetailResponse from(OrderDocument doc) {
        var ds = doc.getDeliverySnapshot();
        var ms = doc.getMartSnapshot();
        var lines = doc.getOrderLines().stream()
                .map(ol -> new OrderLineResponse(
                        ol.productId(),
                        ol.productNameSnapshot(),
                        ol.unitPrice(),
                        ol.quantity(),
                        ol.lineAmount()))
                .toList();

        return new OrderDetailResponse(
                doc.getOrderId(),
                doc.getTossOrderId(),
                doc.getBuyerId(),
                doc.getPayMethod(),
                OrderStatus.valueOf(doc.getStatus()),
                doc.getTotalAmount(),
                ds.zipCode(),
                ds.roadAddress(),
                ds.detailAddress(),
                ms.martId(),
                ms.martName(),
                lines,
                doc.getCreatedAt(),
                doc.getPaidAt(),
                doc.getConfirmedAt(),
                doc.getCanceledAt()
        );
    }
}

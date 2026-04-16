package allmart.orderservice.adapter.webapi.dto;

import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 상세 조회 응답 DTO.
 * order-query-service(MongoDB) 제거 후 order-service DB Replica에서 직접 조회.
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
}

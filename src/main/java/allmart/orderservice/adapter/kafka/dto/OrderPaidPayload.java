package allmart.orderservice.adapter.kafka.dto;

import allmart.orderservice.domain.order.Order;

import java.time.LocalDateTime;
import java.util.List;

/**
 * order.paid.v1 Kafka 이벤트 페이로드
 * receiverPhone은 마스킹된 값으로 포함
 */
public record OrderPaidPayload(
        Long orderId,
        String tossOrderId,
        Long buyerId,
        long totalAmount,
        ShippingAddressDto shippingAddress,
        List<OrderLineDto> orderLines,
        LocalDateTime paidAt
) {
    public OrderPaidPayload {
        orderLines = List.copyOf(orderLines); // 불변 복사 — SpotBugs EI_EXPOSE_REP 방지
    }

    public record ShippingAddressDto(
            String zipCode,
            String address,
            String detailAddress,
            String receiverName,
            String receiverPhone
    ) {}

    public record OrderLineDto(
            Long productId,
            String productName,
            int quantity,
            long unitPrice
    ) {}

    public static OrderPaidPayload from(Order order) {
        var si = order.getShippingInfo();
        var shippingAddress = new ShippingAddressDto(
                si.address().zipCode(),
                si.address().roadAddress(),
                si.address().detailAddress(),
                si.receiverName(),
                si.maskedReceiverPhone()  // 보안 규칙: 전화번호 마스킹
        );

        var orderLines = order.getOrderLines().stream()
                .map(line -> new OrderLineDto(
                        line.productId(),
                        line.productNameSnapshot(),
                        line.quantity(),
                        line.unitPrice().amount()
                ))
                .toList();

        return new OrderPaidPayload(
                order.getId(),
                order.getTossOrderId(),
                order.getBuyerId(),
                order.getTotalAmount().amount(),
                shippingAddress,
                orderLines,
                order.getPaidAt()
        );
    }
}

package allmart.orderservice.adapter.kafka.dto;

import allmart.orderservice.domain.order.Order;

import java.time.LocalDateTime;
import java.util.List;

/**
 * order.paid.v1 Kafka 이벤트 페이로드
 * 수령인명/전화번호 미포함 — DB에서 직접 확인. 로그/이벤트 개인정보 노출 방지.
 */
public record OrderPaidPayload(
        Long orderId,
        String tossOrderId,
        Long buyerId,
        long totalAmount,
        DeliveryAddressDto deliveryAddress,
        MartDto mart,
        List<OrderLineDto> orderLines,
        LocalDateTime paidAt
) {
    public OrderPaidPayload {
        orderLines = List.copyOf(orderLines); // 불변 복사 — SpotBugs EI_EXPOSE_REP 방지
    }

    public record DeliveryAddressDto(
            String zipCode,
            String roadAddress,
            String detailAddress
    ) {}

    public record MartDto(
            Long martId,
            String martName
    ) {}

    public record OrderLineDto(
            Long productId,
            String productName,
            int quantity,
            long unitPrice
    ) {}

    public static OrderPaidPayload from(Order order) {
        var ds = order.getDeliverySnapshot();
        var deliveryAddress = new DeliveryAddressDto(
                ds.zipCode(),
                ds.roadAddress(),
                ds.detailAddress()
        );

        var ms = order.getMartSnapshot();
        var mart = new MartDto(ms.martId(), ms.martName());

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
                deliveryAddress,
                mart,
                orderLines,
                order.getPaidAt()
        );
    }
}

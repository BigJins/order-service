package allmart.orderservice.adapter.kafka.dto;

/** order.reserve.failed.v1 Kafka 메시지 — inventory-service가 재고 부족 시 발행 */
public record OrderReserveFailedMessage(
        String tossOrderId,
        String orderId
) {}

package allmart.orderservice.adapter.kafka.consumer;

import allmart.orderservice.adapter.kafka.dto.OrderReserveFailedMessage;
import allmart.orderservice.application.provided.OrderCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * order.reserve.failed.v1 Kafka 컨슈머.
 * 재고 부족으로 예약 실패 → 주문 자동 취소 (CANCELED).
 * 이미 CANCELED/CONFIRMED 상태면 멱등 무시.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReserveFailedConsumer {

    private final ObjectMapper objectMapper;
    private final OrderCreator orderCreator;

    @KafkaListener(
            topics = "${kafka.topics.order-reserve-failed}",
            groupId = "${kafka.consumer.group-id:order-service}"
    )
    public void onMessage(String value) {
        OrderReserveFailedMessage msg = parseMessage(value);
        log.warn("order.reserve.failed 수신 → 주문 자동 취소: tossOrderId={}", msg.tossOrderId());
        orderCreator.cancelByReserveFailed(msg.tossOrderId());
    }

    /** Debezium EventRouter envelope 대응 */
    private OrderReserveFailedMessage parseMessage(String value) {
        try {
            var root = objectMapper.readTree(value);
            String payload = root.has("payload") ? root.get("payload").asText() : value;
            return objectMapper.readValue(payload, OrderReserveFailedMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("order.reserve.failed 파싱 실패: " + value, e);
        }
    }
}

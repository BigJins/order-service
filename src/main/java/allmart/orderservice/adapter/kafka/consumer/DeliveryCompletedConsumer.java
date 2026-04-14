package allmart.orderservice.adapter.kafka.consumer;

import allmart.orderservice.adapter.kafka.dto.DeliveryCompletedMessage;
import allmart.orderservice.application.provided.OrderCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * delivery.completed.v1 이벤트 수신
 * 배달 완료 시 주문을 CONFIRMED(완료) 상태로 전이
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryCompletedConsumer {

    private final ObjectMapper objectMapper;
    private final OrderCreator orderCreator;

    /** delivery.completed.v1 메시지 수신 — Debezium envelope 처리 후 주문 CONFIRMED 전이 위임 */
    @KafkaListener(topics = "${kafka.topics.delivery-completed}", groupId = "${kafka.consumer.group-id:order-service}")
    public void onMessage(String value) {
        try {
            // Debezium schema envelope 처리: {"schema":...,"payload":"<json>"} 형식 대응
            var root = objectMapper.readTree(value);
            String payload = root.has("payload") ? root.get("payload").asText() : value;

            DeliveryCompletedMessage msg = objectMapper.readValue(payload, DeliveryCompletedMessage.class);
            log.info("delivery.completed.v1 수신: orderId={}", msg.orderId());
            orderCreator.applyDeliveryCompleted(msg.orderId());
        } catch (Exception e) {
            log.warn("delivery.completed.v1 처리 실패, skip: {}", value, e);
        }
    }
}

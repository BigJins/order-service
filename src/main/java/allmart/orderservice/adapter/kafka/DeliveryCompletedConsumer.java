package allmart.orderservice.adapter.kafka;

import allmart.orderservice.adapter.kafka.dto.DeliveryCompletedMessage;
import allmart.orderservice.application.provided.OrderCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * delivery.completed.v1 이벤트 수신
 * 배달 완료 시 주문을 CONFIRMED(완료) 상태로 전이
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class DeliveryCompletedConsumer {

    private final ObjectMapper objectMapper;
    private final OrderCreator orderCreator;

    @KafkaListener(topics = "${kafka.topics.delivery-completed}", groupId = "order-service")
    public void onMessage(String value) {
        try {
            DeliveryCompletedMessage msg = objectMapper.readValue(value, DeliveryCompletedMessage.class);
            log.info("delivery.completed.v1 수신: orderId={}", msg.orderId());
            orderCreator.applyDeliveryCompleted(msg.orderId());
        } catch (Exception e) {
            log.warn("delivery.completed.v1 처리 실패, skip: {}", value, e);
        }
    }
}

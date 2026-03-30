package allmart.orderservice.adapter.kafka;

import allmart.orderservice.adapter.kafka.dto.PaymentResultMessage;
import allmart.orderservice.application.provided.OrderCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Profile("local")
@Component
@RequiredArgsConstructor
@Log4j2
public class PaymentResultConsumer {

    private final ObjectMapper objectMapper;
    private final OrderCreator orderCreator;

    @KafkaListener(topics = "payment.result.v1", groupId = "order-service")
    public void onMessage(String value) {

        log.info("payment.result raw value {}", value);

        PaymentResultMessage msg = parseMessage(value);

        if (msg.isFailed()) {
            orderCreator.applyPaymentFailed(msg.tossOrderId(), msg.paymentKey(), msg.amount());
            return;
        }

        orderCreator.applyPaid(msg.tossOrderId(), msg.paymentKey(), msg.amount());
    }

    private PaymentResultMessage parseMessage(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            JsonNode payloadNode = root.get("payload");

            if (payloadNode == null) {
                // Debezium EventRouter가 payload 컬럼 내용을 직접 Kafka value로 보낸 경우
                return objectMapper.treeToValue(root, PaymentResultMessage.class);
            }

            // payload 필드가 문자열 JSON으로 wrapping된 경우
            String json = objectMapper.readValue(payloadNode.toString(), String.class);
            return objectMapper.readValue(json, PaymentResultMessage.class);

        } catch (Exception e) {
            throw new IllegalArgumentException("payment.result 파싱 실패: " + value, e);
        }
    }
}

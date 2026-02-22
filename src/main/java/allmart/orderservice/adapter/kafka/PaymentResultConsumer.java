package allmart.orderservice.adapter.kafka;

import allmart.orderservice.adapter.kafka.dto.PaymentResultMessage;
import allmart.orderservice.application.provided.OrderCreator;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
                throw new IllegalArgumentException("payload가 없습니다: " + value);
            }

            // payload가 문자열 JSON이라고 확정된 케이스
            String json = objectMapper.readValue(payloadNode.toString(), String.class);
            return objectMapper.readValue(json, PaymentResultMessage.class);

        } catch (Exception e) {
            throw new IllegalArgumentException("payment.result 파싱 실패: " + value, e);
        }
    }
}

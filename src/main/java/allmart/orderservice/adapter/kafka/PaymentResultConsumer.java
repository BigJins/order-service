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

    @KafkaListener(topics = "${kafka.topics.payment-result}", groupId = "order-service")
    public void onMessage(String value) {

        PaymentResultMessage msg = parseMessage(value);
        // paymentKey는 민감정보 — 로그에 포함하지 않음
        log.info("payment.result 수신: tossOrderId={}, status={}", msg.tossOrderId(), msg.isFailed() ? "FAILED" : "DONE");

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

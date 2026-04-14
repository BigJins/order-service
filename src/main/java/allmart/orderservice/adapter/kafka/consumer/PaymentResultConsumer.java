package allmart.orderservice.adapter.kafka.consumer;

import allmart.orderservice.adapter.kafka.dto.PaymentResultMessage;
import allmart.orderservice.application.provided.OrderCreator;
import allmart.orderservice.domain.order.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * payment.result.v1 Kafka 컨슈머.
 * DONE → 주문 PAID 전이, FAILED → 주문 PAYMENT_FAILED 전이.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultConsumer {

    private final ObjectMapper objectMapper;
    private final OrderCreator orderCreator;

    /** 결제 결과 메시지 수신 — Debezium envelope 처리 후 상태 전이 위임 */
    @KafkaListener(topics = "${kafka.topics.payment-result}", groupId = "${kafka.consumer.group-id:order-service}")
    public void onMessage(String value) {
        PaymentResultMessage msg = parseMessage(value);
        // paymentKey는 민감정보 — 로그에 포함하지 않음
        log.info("payment.result 수신: tossOrderId={}, status={}", msg.tossOrderId(), msg.isFailed() ? "FAILED" : "DONE");

        Money amount = Money.of(msg.amount());
        if (msg.isFailed()) {
            orderCreator.applyPaymentFailed(msg.tossOrderId(), msg.paymentKey(), amount);
        } else {
            orderCreator.applyPaid(msg.tossOrderId(), msg.paymentKey(), amount);
        }
    }

    /** Debezium EventRouter envelope 대응 — payload 필드가 있으면 내부 JSON, 없으면 전체를 역직렬화 */
    private PaymentResultMessage parseMessage(String value) {
        try {
            var root = objectMapper.readTree(value);
            // Debezium EventRouter: payload 필드가 있으면 그 값을, 없으면 전체 메시지를 파싱
            String payload = root.has("payload") ? root.get("payload").asText() : value;
            return objectMapper.readValue(payload, PaymentResultMessage.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("payment.result 파싱 실패: " + value, e);
        }
    }
}

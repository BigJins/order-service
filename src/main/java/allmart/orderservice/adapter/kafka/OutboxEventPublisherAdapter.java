package allmart.orderservice.adapter.kafka;

import allmart.orderservice.adapter.kafka.dto.OrderPaidPayload;
import allmart.orderservice.application.required.OutboxEventPublisher;
import allmart.orderservice.application.required.OutboxRepository;
import allmart.orderservice.domain.event.OutboxEvent;
import allmart.orderservice.domain.order.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * OutboxEventPublisher 구현체
 * Order → JSON 직렬화 → outbox_event 테이블 저장
 * 저장은 호출자의 트랜잭션 내에서 실행됨 (Outbox 패턴 핵심)
 */
@Service
@RequiredArgsConstructor
public class OutboxEventPublisherAdapter implements OutboxEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publishOrderPaid(Order order) {
        try {
            String json = objectMapper.writeValueAsString(OrderPaidPayload.from(order));
            outboxRepository.save(OutboxEvent.create(
                    "order.paid.v1",
                    "order",
                    String.valueOf(order.getId()),
                    json
            ));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "order.paid.v1 이벤트 직렬화 실패: orderId=" + order.getId(), e);
        }
    }
}

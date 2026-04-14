package allmart.orderservice.adapter.kafka;

import allmart.orderservice.adapter.kafka.dto.OrderCanceledPayload;
import allmart.orderservice.adapter.kafka.dto.OrderCreatedPayload;
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

    /** Order → OrderCreatedPayload JSON → outbox_event 저장 */
    @Override
    public void publishOrderCreated(Order order) {
        save("order.created.v1", order.getId(), OrderCreatedPayload.from(order));
    }

    /** Order → OrderPaidPayload JSON → outbox_event 저장 */
    @Override
    public void publishOrderPaid(Order order) {
        save("order.paid.v1", order.getId(), OrderPaidPayload.from(order));
    }

    /** Order → OrderCreatedPayload JSON → outbox_event 저장 (상태 스냅샷 재활용) */
    @Override
    public void publishOrderFailed(Order order) {
        save("order.failed.v1", order.getId(), OrderCreatedPayload.from(order));
    }

    /** Order → OrderCreatedPayload JSON → outbox_event 저장 */
    @Override
    public void publishOrderConfirmed(Order order) {
        save("order.confirmed.v1", order.getId(), OrderCreatedPayload.from(order));
    }

    /** Order → OrderCanceledPayload JSON → outbox_event 저장 */
    @Override
    public void publishOrderCanceled(Order order) {
        save("order.canceled.v1", order.getId(), OrderCanceledPayload.from(order));
    }

    /** 페이로드 직렬화 후 OutboxEvent 저장 — 직렬화 실패는 IllegalStateException으로 전파 */
    private void save(String topic, Long orderId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(OutboxEvent.create(topic, "order", String.valueOf(orderId), json));
        } catch (Exception e) {
            throw new IllegalStateException(topic + " 이벤트 직렬화 실패: orderId=" + orderId, e);
        }
    }
}

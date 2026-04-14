package allmart.orderservice.domain.event;

import allmart.orderservice.domain.AbstractEntity;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox 패턴 이벤트 엔티티.
 * 주문 저장과 동일 트랜잭션에 저장 → Debezium CDC가 감지 후 Kafka로 릴레이.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends AbstractEntity {

    // ORDER_CREATED 같은 타입
    private String eventType;

    // Order Aggregate ID
    private String aggregateId;

    // order, payment 등
    private String aggregateType;

    // 실제 이벤트 데이터(JSON)
    private String payload;

    private LocalDateTime createdAt;

    /** Outbox 이벤트 생성 (정적 팩토리) */
    public static OutboxEvent create(
            String eventType,
            String aggregateType,
            String aggregateId,
            String payload
    ) {
        OutboxEvent event = new OutboxEvent();
        event.eventType = eventType;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.payload = payload;
        event.createdAt = LocalDateTime.now();
        return event;
    }
}

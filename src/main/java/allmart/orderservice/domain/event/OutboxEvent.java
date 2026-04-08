package allmart.orderservice.domain.event;

import allmart.orderservice.config.SnowflakeGenerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @SnowflakeGenerated
    private Long id;

    // ORDER_CREATED 같은 타입
    private String eventType;

    // Order Aggregate ID
    private String aggregateId;

    // order, payment 등
    private String aggregateType;

    // 실제 이벤트 데이터(JSON)
    private String payload;

    private LocalDateTime createdAt;

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

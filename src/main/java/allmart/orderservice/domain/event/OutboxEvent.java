package allmart.orderservice.domain.event;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_event",
        indexes = {
                @Index(name = "idx_outbox_created_at", columnList = "created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ORDER_CREATED 같은 타입
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    // Order Aggregate ID
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    // order, payment 등
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    // 실제 이벤트 데이터(JSON)
    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;


    @Column(name = "created_at", nullable = false, updatable = false)
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

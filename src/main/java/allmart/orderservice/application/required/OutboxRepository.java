package allmart.orderservice.application.required;

import allmart.orderservice.domain.event.OutboxEvent;
import org.springframework.data.repository.Repository;

/** Outbox 이벤트 저장소 — 삽입 전용, 삭제는 Debezium CDC가 담당 */
public interface OutboxRepository extends Repository<OutboxEvent, Long> {
    OutboxEvent save(OutboxEvent outboxEvent);
}

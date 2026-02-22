package allmart.orderservice.application.required;

import allmart.orderservice.domain.event.OutboxEvent;
import org.springframework.data.repository.Repository;

public interface OutboxRepository extends Repository<OutboxEvent,Long> {
    OutboxEvent save(Object outboxEvent);
}

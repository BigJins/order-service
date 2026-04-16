package allmart.orderservice.application.required;

import allmart.orderservice.domain.event.OutboxEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/** Outbox 이벤트 저장소 */
public interface OutboxRepository extends Repository<OutboxEvent, Long> {

    OutboxEvent save(OutboxEvent outboxEvent);

    /**
     * 배치 재발행 대상 조회 — publishedAt IS NULL AND createdAt &lt; cutoff.
     * CDC 정상 경로는 이 조건에 해당하지 않는다고 가정(CDC가 5분 이내에 처리).
     * 5분 이상 미발행 이벤트만 배치 재발행.
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.publishedAt IS NULL AND o.createdAt < :cutoff ORDER BY o.createdAt ASC")
    Page<OutboxEvent> findUnpublished(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}

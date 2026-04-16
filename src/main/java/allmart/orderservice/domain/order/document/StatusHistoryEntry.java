package allmart.orderservice.domain.order.document;

import java.time.LocalDateTime;

/** 주문 상태 변경 이력 항목 — append-only (배민 패턴) */
public record StatusHistoryEntry(
        String status,
        LocalDateTime changedAt
) {}

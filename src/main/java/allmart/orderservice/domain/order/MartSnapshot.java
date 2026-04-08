package allmart.orderservice.domain.order;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import static java.util.Objects.requireNonNull;

/**
 * 주문 당시 마트 정보 스냅샷 (불변).
 * 마트명이 나중에 변경되어도 주문내역엔 주문 당시 이름이 그대로 유지됨.
 */
@Embeddable
public record MartSnapshot(
        @Column(name = "mart_id", nullable = false)
        Long martId,

        @Column(name = "mart_name", nullable = false, length = 100)
        String martName,

        @Nullable
        @Column(name = "mart_phone", length = 20)
        String martPhone
) {
    public MartSnapshot {
        requireNonNull(martId, "martId is required");
        requireNonNull(martName, "martName is required");

        if (martId <= 0) throw new IllegalArgumentException("martId must be > 0");

        martName = martName.trim();
        if (martName.isBlank()) throw new IllegalArgumentException("martName must not be blank");
        if (martName.length() > 100) throw new IllegalArgumentException("martName must be <= 100");

        if (martPhone != null) {
            martPhone = martPhone.trim().isEmpty() ? null : martPhone.trim();
        }
    }

}

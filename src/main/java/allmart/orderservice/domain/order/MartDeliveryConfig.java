package allmart.orderservice.domain.order;

import allmart.orderservice.domain.AbstractEntity;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 마트 사장님이 설정하는 배달료 정책.
 * 주문 생성 시 이 값을 읽어 MartSnapshot과 Order 필드에 반영한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MartDeliveryConfig extends AbstractEntity {

    private Long martId;

    private long deliveryFeeAmount;

    private long freeDeliveryThreshold;

    private LocalDateTime updatedAt;

    public static MartDeliveryConfig create(Long martId, long deliveryFeeAmount, long freeDeliveryThreshold) {
        validate(deliveryFeeAmount, freeDeliveryThreshold);
        MartDeliveryConfig config = new MartDeliveryConfig();
        config.martId = martId;
        config.deliveryFeeAmount = deliveryFeeAmount;
        config.freeDeliveryThreshold = freeDeliveryThreshold;
        config.updatedAt = LocalDateTime.now();
        return config;
    }

    public void update(long deliveryFeeAmount, long freeDeliveryThreshold) {
        validate(deliveryFeeAmount, freeDeliveryThreshold);
        this.deliveryFeeAmount = deliveryFeeAmount;
        this.freeDeliveryThreshold = freeDeliveryThreshold;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validate(long deliveryFeeAmount, long freeDeliveryThreshold) {
        if (deliveryFeeAmount < 0) throw new IllegalArgumentException("배달료는 0원 이상이어야 합니다.");
        if (freeDeliveryThreshold < 0) throw new IllegalArgumentException("무료배달 기준금액은 0원 이상이어야 합니다.");
    }
}

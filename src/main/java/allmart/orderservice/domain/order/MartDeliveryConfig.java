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

    private Money deliveryFee;

    private Money freeDeliveryThreshold;

    private LocalDateTime updatedAt;

    /** 마트 배달 설정 최초 생성 */
    public static MartDeliveryConfig create(Long martId, Money deliveryFee, Money freeDeliveryThreshold) {
        MartDeliveryConfig config = new MartDeliveryConfig();
        config.martId               = martId;
        config.deliveryFee          = deliveryFee;
        config.freeDeliveryThreshold = freeDeliveryThreshold;
        config.updatedAt            = LocalDateTime.now();
        return config;
    }

    /** 배달료·무료 기준 금액 수정 */
    public void update(Money deliveryFee, Money freeDeliveryThreshold) {
        this.deliveryFee          = deliveryFee;
        this.freeDeliveryThreshold = freeDeliveryThreshold;
        this.updatedAt            = LocalDateTime.now();
    }

    /** 주문 금액에 따라 배달료 반환. 무료 기준 이상이면 0원 */
    public Money calculateFee(Money productTotal) {
        if (productTotal.isGreaterThanOrEqualTo(freeDeliveryThreshold)) return Money.zero();
        return deliveryFee;
    }
}

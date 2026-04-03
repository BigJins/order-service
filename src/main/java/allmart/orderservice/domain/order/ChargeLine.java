package allmart.orderservice.domain.order;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import static java.util.Objects.requireNonNull;

/**
 * 주문 요금 명세 항목.
 * 예) SUBTOTAL 23,000 / DELIVERY_FEE 3,000 / DISCOUNT -2,000
 */
@Embeddable
public record ChargeLine(
        @Enumerated(EnumType.STRING)
        @Column(name = "charge_type", nullable = false, length = 30)
        ChargeType type,

        @Embedded
        @AttributeOverride(name = "amount", column = @Column(name = "charge_amount", nullable = false))
        Money amount
) {
    public ChargeLine {
        requireNonNull(type, "type is required");
        requireNonNull(amount, "amount is required");
    }
}

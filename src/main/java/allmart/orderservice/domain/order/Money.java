package allmart.orderservice.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public record Money(
        @Column(name = "amount", nullable = false)
        long amount
) {

    public Money {
        if (amount < 0) throw new IllegalArgumentException("Amount must be not negative");
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other);
        return new Money(Math.addExact(this.amount, other.amount));
    }

    public Money multiply(int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Quantity must be not negative");
        return new Money(Math.multiplyExact(amount, (long) quantity));
    }

    public static Money zero() {
        return new Money(0);
    }
}

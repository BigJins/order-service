package allmart.orderservice.domain.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

@Embeddable
public record Money(
        @JsonValue
        @Column(name = "amount", nullable = false)
        long amount
) {

    public Money {
        if (amount < 0) throw new IllegalArgumentException("Amount must be not negative");
    }

    @JsonCreator
    public static Money of(long amount) {
        return new Money(amount);
    }

    public Money plus(Money other) {
        Objects.requireNonNull(other);
        return new Money(Math.addExact(this.amount, other.amount));
    }

    public Money multiply(int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Quantity must be not negative");
        return new Money(Math.multiplyExact(amount, (long) quantity));
    }

    public Money minus(Money other) {
        Objects.requireNonNull(other);
        if (this.amount < other.amount) throw new IllegalArgumentException("Amount must be not negative");
        return new Money(this.amount - other.amount);
    }

    public Money divide(double divisor) {
        if (divisor <= 0) throw new IllegalArgumentException("Divisor must be positive");
        return new Money(Math.round(this.amount / divisor));
    }

    public static Money zero() {
        return new Money(0);
    }
}

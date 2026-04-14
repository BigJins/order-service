package allmart.orderservice.domain.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/** 금액 값 객체 (원 단위, 음수 불가). 모든 금액 연산은 이 타입을 통해 수행한다. */
@Embeddable
public record Money(
        @JsonValue
        @Column(name = "amount", nullable = false)
        long amount
) {

    public Money {
        if (amount < 0) throw new IllegalArgumentException("Amount must be not negative");
    }

    /** Jackson 역직렬화용 — long 값을 Money로 변환 */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Money of(long amount) {
        return new Money(amount);
    }

    /** 덧셈 — 오버플로우 시 ArithmeticException */
    public Money plus(Money other) {
        Objects.requireNonNull(other);
        return new Money(Math.addExact(this.amount, other.amount));
    }

    /** 수량 곱셈 — 단가 × 수량으로 행 금액 계산 시 사용 */
    public Money multiply(int quantity) {
        if (quantity < 0) throw new IllegalArgumentException("Quantity must be not negative");
        return new Money(Math.multiplyExact(amount, (long) quantity));
    }

    /** 뺄셈 — 결과가 음수이면 예외 (음수 금액 방지) */
    public Money minus(Money other) {
        Objects.requireNonNull(other);
        if (this.amount < other.amount) throw new IllegalArgumentException("Amount must be not negative");
        return new Money(this.amount - other.amount);
    }

    /** 나눗셈 — 공급가액 환산(÷ 1.1) 등 소수 제수 지원, 반올림 처리 */
    public Money divide(double divisor) {
        if (divisor <= 0) throw new IllegalArgumentException("Divisor must be positive");
        return new Money(Math.round(this.amount / divisor));
    }

    /** 비교 — 무료 배달 기준 충족 여부 판단 등에 사용 */
    public boolean isGreaterThanOrEqualTo(Money other) {
        Objects.requireNonNull(other);
        return this.amount >= other.amount;
    }

    /** 0원 초기값 */
    public static Money zero() {
        return new Money(0);
    }
}

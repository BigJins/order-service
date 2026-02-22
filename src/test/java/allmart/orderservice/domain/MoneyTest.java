package allmart.orderservice.domain;

import allmart.orderservice.domain.order.Money;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void plusMoney() {
        Money money = new Money(10);

        assertThat(money.plus(new Money(20))).isEqualTo(new Money(30));
    }

    @Test
    void multiplyMoney() {
        Money money = new Money(10);

        assertThat(money.multiply(3)).isEqualTo(new Money(30));
    }

    @Test
    void notNegativeMoney() {
        assertThatThrownBy(() -> new Money(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroAmount() {
        assertThat(Money.zero()).isEqualTo(new Money(0));
    }

}
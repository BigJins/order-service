package allmart.orderservice.domain;

import allmart.orderservice.domain.order.Money;
import allmart.orderservice.domain.order.OrderLine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OderLineTest {

    @Test
    void blankProductName_throws() {
        assertThatThrownBy(() ->
                new OrderLine(1L, "   ", new Money(1000), 1)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullProductName_throws() {
        assertThatThrownBy(() ->
                new OrderLine(1L, null, new Money(1000), 1)
        ).isInstanceOf(NullPointerException.class);
        
    }

    @Test
    void trimApplied() {
        OrderLine line = new OrderLine(
                1L,
                "   사과   ",
                new Money(1000),
                2
        );

        assertThat(line.productNameSnapshot()).isEqualTo("사과");
    }

    @Test
    void lineAmountCalculated() {
        OrderLine line = new OrderLine(
                1L,
                "사과",
                new Money(1000),
                3
        );

        assertThat(line.lineAmount())
                .isEqualTo(new Money(3000));
    }
}
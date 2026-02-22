package allmart.orderservice.domain;

import allmart.orderservice.domain.order.Address;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AddressTest {

    @Test
    void createAddress() {
        var a1 = new Address("47352", "부산광역시", "범내골역4번출구");

        assertThat(a1).isNotNull();
    }

    @Test
    void wrongZipCode() {
        // 5자리만 허용
        assertThatThrownBy(() -> new Address("473528", "부산광역시", "범내골역4번출구"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid zip code");
    }

    @Test
    void wrongZipCodeBlank() {
        assertThatThrownBy(() -> new Address("   ", "부산광역시", "범내골역4번출구"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zipCode must not be blank");
    }

    @Test
    void wrongRoadAddress() {
        assertThatThrownBy(() -> new Address("47352", " ", "범내골역4번출구"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("roadAddress must not be blank");
    }

    @Test
    void wrongDetailAddress() {
        assertThatThrownBy(() -> new Address("47352", "부산광역시", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("detailAddress must not be blank");
    }

    @Test
    void trim() {
        var a1 = new Address("47352 ", " 부산광역시", " 범내골역4번출구 ");

        assertThat(a1.zipCode()).isEqualTo("47352");
        assertThat(a1.roadAddress()).isEqualTo("부산광역시");
        assertThat(a1.detailAddress()).isEqualTo("범내골역4번출구");
    }

    @Test
    void fullAddress() {
        var a1 = new Address("47352", "부산광역시", "범내골역4번출구");

        var f1 = a1.fullAddress();

        // Address.fullAddress()는 "(47352) 부산광역시 범내골역4번출구" 포맷
        assertThat(f1).isEqualTo("(47352) 부산광역시 범내골역4번출구");
    }
}

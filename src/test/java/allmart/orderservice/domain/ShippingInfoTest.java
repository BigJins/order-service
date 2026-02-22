package allmart.orderservice.domain;

import allmart.orderservice.domain.order.Address;
import allmart.orderservice.domain.order.ShippingInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShippingInfoTest {

    private final Address address = new Address("47352", "부산광역시", "범내골역4번출구");

    @Test
    void trim() {
        var s1 = new ShippingInfo("김아무개", "01012345678", address, "잘 부탁드려요");
        var s2 = new ShippingInfo(" 김아무개", "01012345678 ", address, " 잘 부탁드려요 ");

        assertThat(s1).isEqualTo(s2);
    }

    @Test
    void deliveryMemoBlankBecomesNull() {
        var s = new ShippingInfo("김아무개", "01012345678", address, "   ");

        assertThat(s.deliveryMemo()).isNull();
    }

    @Test
    void deliveryMemoTooLong() {
        var memo = "a".repeat(201);

        assertThatThrownBy(() -> new ShippingInfo("김아무개", "01012345678", address, memo))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shippingInfoNullCheck() {
        assertThatThrownBy(() -> new ShippingInfo(null, "01012345678", address, "잘 부탁드려요"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("receiverName is required");

        assertThatThrownBy(() -> new ShippingInfo("김말똥", null, address, "잘 부탁드려요"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("receiverPhone is required");
    }

    @Test
    void receiverNameNonBlank() {
        assertThatThrownBy(() -> new ShippingInfo("", "01012345678", address, "잘 부탁드려요"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void receiverNameLimitLength() {
        assertThatThrownBy(() -> new ShippingInfo("김", "01012345678", address, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeAddress() {
        var original = new ShippingInfo("김아무개", "01012345678", address, "잘 부탁드려요");
        var newAddress = new Address("47352", "부산광역시", "범내골역4번출구");

        var changed = original.withAddress(newAddress);

        assertThat(changed.address()).isEqualTo(newAddress);
        assertThat(original.address()).isEqualTo(address);
    }

    @Test
    void changeAddressNonNull() {
        var s = new ShippingInfo("김아무개", "01012345678", address, "잘 부탁드려요");

        var ex = assertThrows(NullPointerException.class, () -> s.withAddress(null));
        assertThat(ex).hasMessage("newAddress is required");
    }

    @Test
    void changeDeliveryMemo() {
        var s = new ShippingInfo("김아무개", "01012345678", address, "잘 부탁드려요");

        var changedMemo = s.withDeliveryMemo("부재시 경비실");

        assertThat(changedMemo.deliveryMemo()).isEqualTo("부재시 경비실");
        assertThat(s.deliveryMemo()).isEqualTo("잘 부탁드려요");
    }

    @Test
    void changeReceiverName() {
        var s = new ShippingInfo("김아무개", "01012345678", address, "잘 부탁드려요");

        var changedName = s.withReceiverName("이무개");

        assertThat(changedName.receiverPhone()).isEqualTo(s.receiverPhone());
        assertThat(changedName.address()).isEqualTo(s.address());
        assertThat(changedName.receiverName()).isEqualTo("이무개");
    }

    @Test
    void changeReceiverPhone() {
        var s = new ShippingInfo("김아무개", "01012345678", address, "잘 부탁드려요");

        var changedPhone = s.withReceiverPhone("01078974561");

        assertThat(changedPhone.receiverPhone()).isEqualTo("01078974561");
        assertThat(changedPhone.address()).isEqualTo(s.address());
        assertThat(changedPhone.receiverName()).isEqualTo(s.receiverName());
        assertThat(s.receiverPhone()).isEqualTo("01012345678");
    }

    @Test
    void wrongChangeReceiverPhone() {
        var s = new ShippingInfo("김아무개", "01012345678", address, "잘 부탁드려요");

        assertThrows(IllegalArgumentException.class, () -> s.withReceiverPhone("011123456"));
    }

    @Test
    void hasDeliveryMemoTrue() {
        var s = new ShippingInfo("김아무개", "01012345678", address, "잘 부탁드려요");

        assertThat(s.hasDeliveryMemo()).isTrue();
    }

    @Test
    void hasDeliveryMemoFalse() {
        var s = new ShippingInfo("김아무개", "01012345678", address, "");

        assertThat(s.hasDeliveryMemo()).isFalse();
        assertThat(s.deliveryMemo()).isNull();
    }

    @Test
    void maskedPhone() {
        var s = new ShippingInfo("김아무개", "01012345678", address, "");

        assertThat(s.maskedReceiverPhone()).isEqualTo("010****5678");
    }
}

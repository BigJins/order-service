package allmart.orderservice.domain.order;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;

import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

@Embeddable
public record ShippingInfo(
        @Column(name = "receiver_name", nullable = false, length = 50)
        String receiverName,

        @Column(name = "receiver_phone", nullable = false, length = 20)
        String receiverPhone,

        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "zipCode", column = @Column(name = "shipping_zip_code", nullable = false, length = 10)),
                @AttributeOverride(name = "roadAddress", column = @Column(name = "shipping_road_address", nullable = false, length = 200)),
                @AttributeOverride(name = "detailAddress", column = @Column(name = "shipping_detail_address", nullable = false, length = 200))
        })
        Address address,

        @Nullable
        @Column(name = "delivery_memo", length = 200)
        String deliveryMemo
) {
    private static final Pattern RECEIVER_PHONE_PATTERN = Pattern.compile("^010\\d{8}$");
    private static final int RECEIVER_NAME_MIN = 2;
    private static final int RECEIVER_NAME_MAX = 50;
    private static final int DELIVERY_MEMO_MAX = 200;

    public ShippingInfo {
        receiverName = normalizeReceiverName(receiverName);
        receiverPhone = normalizeReceiverPhone(receiverPhone);
        requireNonNull(address, "address is required");
        deliveryMemo = normalizeDeliveryMemo(deliveryMemo);
    }

    public ShippingInfo withAddress(Address newAddress) {
        requireNonNull(newAddress, "newAddress is required");
        return new ShippingInfo(this.receiverName, this.receiverPhone, newAddress, this.deliveryMemo);
    }

    public ShippingInfo withDeliveryMemo(String newDeliveryMemo) {
        return new ShippingInfo(this.receiverName, this.receiverPhone, this.address, normalizeDeliveryMemo(newDeliveryMemo));
    }

    public ShippingInfo withReceiverName(String newReceiverName) {
        return new ShippingInfo(normalizeReceiverName(newReceiverName), this.receiverPhone, this.address, this.deliveryMemo);
    }

    public ShippingInfo withReceiverPhone(String newReceiverPhone) {
        return new ShippingInfo(this.receiverName, normalizeReceiverPhone(newReceiverPhone), this.address, this.deliveryMemo);
    }

    public boolean hasDeliveryMemo() {
        return this.deliveryMemo != null;
    }

    public String maskedReceiverPhone() {
        // 이미 normalize에서 검증됨, 방어적으로 한번 더 호출해도 됨
        String p = normalizeReceiverPhone(receiverPhone);
        return p.substring(0, 3) + "****" + p.substring(7);
    }

    private static String normalizeDeliveryMemo(String memo) {
        if (memo == null) return null;
        memo = memo.trim();
        if (memo.isBlank()) return null;
        if (memo.length() > DELIVERY_MEMO_MAX) throw new IllegalArgumentException("deliveryMemo must be <= " + DELIVERY_MEMO_MAX);
        return memo;
    }

    private static String normalizeReceiverName(String name) {
        requireNonNull(name, "receiverName is required");
        name = name.trim();
        if (name.isBlank()) throw new IllegalArgumentException("receiverName is blank");
        if (name.length() < RECEIVER_NAME_MIN || name.length() > RECEIVER_NAME_MAX)
            throw new IllegalArgumentException("receiverName must be between " + RECEIVER_NAME_MIN + " and " + RECEIVER_NAME_MAX);
        return name;
    }

    private static String normalizeReceiverPhone(String phone) {
        requireNonNull(phone, "receiverPhone is required");
        phone = phone.trim();
        if (!RECEIVER_PHONE_PATTERN.matcher(phone).matches())
            throw new IllegalArgumentException("receiverPhone is invalid");
        return phone;
    }
}

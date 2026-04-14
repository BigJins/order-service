package allmart.orderservice.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * 주문 당시 배송지 스냅샷 (불변).
 * 수령인 이름/전화번호는 저장하지 않음 — DB에서 직접 확인 가능, 이벤트/로그 노출 방지.
 * 저장된 주소(SavedAddress)는 auth-service에서 관리.
 */
@Embeddable
public record DeliverySnapshot(
        @Column(name = "zip_code", nullable = false, length = 10)
        String zipCode,

        @Column(name = "road_address", nullable = false, length = 200)
        String roadAddress,

        @Column(name = "detail_address", nullable = false, length = 200)
        String detailAddress
) {
    private static final Pattern ZIP_CODE_PATTERN = Pattern.compile("^\\d{5}$");

    public DeliverySnapshot {
        requireNonNull(zipCode, "zipCode is required");
        requireNonNull(roadAddress, "roadAddress is required");
        requireNonNull(detailAddress, "detailAddress is required");

        zipCode = zipCode.trim();
        roadAddress = roadAddress.trim();
        detailAddress = detailAddress.trim();

        if (!ZIP_CODE_PATTERN.matcher(zipCode).matches())
            throw new IllegalArgumentException("Invalid zip code: " + zipCode);
        if (roadAddress.isBlank())
            throw new IllegalArgumentException("roadAddress must not be blank");
        if (detailAddress.isBlank())
            throw new IllegalArgumentException("detailAddress must not be blank");
    }

    /** 우편번호 + 도로명주소 + 상세주소 한 줄 문자열 */
    public String fullAddress() {
        return "(" + zipCode + ") " + roadAddress + " " + detailAddress;
    }
}

package allmart.orderservice.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

@Embeddable
public record Address (
        @Column(name = "zip_code", nullable = false, length = 10)
        String zipCode,
        @Column(name = "road_address", nullable = false, length = 200)
        String roadAddress,
        @Column(name = "detail_address", nullable = false, length = 200)
        String detailAddress
) {
    private static final Pattern ZIP_CODE_PATTERN = Pattern.compile("^\\d{5}$");
    private static final int ROAD_ADDRESS_MAX = 200;
    private static final int DETAIL_ADDRESS_MAX = 200;

    public Address {
        zipCode = normalizeRequired(zipCode, "zipCode", 10);
        roadAddress = normalizeRequired(roadAddress, "roadAddress", ROAD_ADDRESS_MAX);
        detailAddress = normalizeRequired(detailAddress, "detailAddress", DETAIL_ADDRESS_MAX);

        if (!ZIP_CODE_PATTERN.matcher(zipCode).matches()) {
            throw new IllegalArgumentException("Invalid zip code: " + zipCode);
        }
    }

    public String fullAddress() {
        return "(" + zipCode + ") " + roadAddress + " " + detailAddress;
    }

    private static String normalizeRequired(String value, String fieldName, int maxLength) {
        requireNonNull(value, fieldName + " is required");
        String trimmed = value.trim();
        if (trimmed.isBlank()) throw new IllegalArgumentException(fieldName + " must not be blank");
        if (trimmed.length() > maxLength) throw new IllegalArgumentException(fieldName + " must be <= " + maxLength);
        return trimmed;
    }
}
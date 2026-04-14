package allmart.orderservice.domain.order;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 주문 요청사항.
 * - orderRequest: 판매자(마트)에게 전달 (예: "덜 맵게 해주세요")
 * - deliveryRequest: 배달기사에게 전달 (예: "문 앞에 두세요")
 * 빈 문자열 입력 시 null로 정규화.
 */
@Embeddable
public record OrderMemo(
        @Nullable
        @Column(name = "order_request", length = 200)
        String orderRequest,

        @Nullable
        @Column(name = "delivery_request", length = 200)
        String deliveryRequest
) {
    public OrderMemo {
        orderRequest = normalize(orderRequest);
        deliveryRequest = normalize(deliveryRequest);
    }

    /** 빈 문자열 → null 정규화, 200자 초과 시 예외 */
    private static String normalize(@Nullable String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isBlank()) return null;
        if (trimmed.length() > 200) throw new IllegalArgumentException("요청사항은 200자 이하여야 합니다.");
        return trimmed;
    }
}

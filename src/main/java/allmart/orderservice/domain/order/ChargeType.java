package allmart.orderservice.domain.order;

/** 주문 요금 유형 열거형. ChargeLine.type 에 사용된다. */
public enum ChargeType {
    SUBTOTAL,           // 상품 소계
    DELIVERY_FEE,       // 배달팁 (VAT 포함)
    DELIVERY_SUPPLY,    // 배달팁 공급가액
    DELIVERY_VAT,       // 배달팁 부가세
    DISCOUNT,           // 할인쿠폰
    POINT;              // 포인트 사용

    /** 부가세 포함 금액 → 공급가액 환산 제수 (10% VAT 기준) */
    public static final double VAT_DIVISOR = 1.1;
}

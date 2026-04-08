package allmart.orderservice.domain.order;

public enum ChargeType {
    SUBTOTAL,           // 상품 소계
    DELIVERY_FEE,       // 배달팁 (VAT 포함)
    DELIVERY_SUPPLY,    // 배달팁 공급가액
    DELIVERY_VAT,       // 배달팁 부가세
    DISCOUNT,           // 할인쿠폰
    POINT               // 포인트 사용
}

package allmart.orderservice.domain.order;

public enum ChargeType {
    SUBTOTAL,       // 상품 소계
    DELIVERY_FEE,   // 배달팁
    DISCOUNT,       // 할인쿠폰
    POINT           // 포인트 사용
}

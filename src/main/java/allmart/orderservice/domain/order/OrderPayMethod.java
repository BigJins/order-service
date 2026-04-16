package allmart.orderservice.domain.order;

/** 주문 결제 수단 열거형. 후불 수단 여부는 isOnDeliveryPayment()로 판별한다. */
public enum OrderPayMethod {
    CARD,              // 카드 (Toss 온라인 결제)
    CASH_ON_DELIVERY,  // 현금 후불 (배달 완료 시 현금 수령)
    CARD_ON_DELIVERY,  // 카드 후불 (배달 완료 시 카드 단말기 결제)
    CART_ON_DELIVERY,  // 카트 후불 (장바구니 후불 — 배달 완료 후 앱 내 카드 자동 청구)
    POINT,             // 포인트 전액
    MIXED;             // 포인트 일부 + 카드

    /** pay-service를 거치지 않는 후불 결제 수단 */
    public boolean isOnDeliveryPayment() {
        return this == CASH_ON_DELIVERY || this == CARD_ON_DELIVERY || this == CART_ON_DELIVERY;
    }
}

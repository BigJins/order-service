package allmart.orderservice.domain.order;

public enum OrderPayMethod {
    CARD,             // 카드 (Toss 온라인 결제)
    CASH_PREPAID,     // 현금 선불 (판매자가 수령 확인 후 PAID 처리)
    CASH_ON_DELIVERY, // 현금 후불 (배달 완료 시 수령)
    POINT,            // 포인트 전액
    MIXED             // 포인트 일부 + 카드
}

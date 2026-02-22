package allmart.orderservice.domain.order;

public enum OrderStatus {

    // 결제 대기 (주문 제출 직후)
    PENDING_PAYMENT {
        @Override
        void validatePayable() {}
    },

    // 결제 승인 완료
    PAID {
        @Override
        void validatePayable() {
            throw new IllegalStateException("이미 결제 됨");
        }
    },
    CONFIRMED,         // 재고/배송까지 확정 (옵션)
    PAYMENT_FAILED,    // 결제 실패
    CANCELED;          // 취소

    void validatePayable() {}
}



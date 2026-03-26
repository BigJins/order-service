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
    CONFIRMED {         // 재고/배송까지 확정 (옵션)
        @Override
        void validatePayable() {
            throw new IllegalStateException("이미 확정된 주문입니다.");
        }
    },
    PAYMENT_FAILED,    // 결제 실패 — 재시도 허용 (PENDING_PAYMENT로 복귀 후 재결제)
    CANCELED {         // 취소 — 터미널 상태
        @Override
        void validatePayable() {
            throw new IllegalStateException("취소된 주문은 결제할 수 없습니다.");
        }
    };

    void validatePayable() {}
}



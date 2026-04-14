package allmart.orderservice.domain.order;

/**
 * 주문 상태 열거형 및 상태 전이 유효성 검증.
 * 각 상수가 허용/금지 전이를 스스로 정의 — 잘못된 전이 시 IllegalStateException.
 */
public enum OrderStatus {

    // 결제 대기 (주문 제출 직후)
    PENDING_PAYMENT {
        @Override void validatePayable()    {}
        @Override void validateFailable()   {}
        @Override void validateCancelable() {}
    },

    // 결제 승인 완료
    PAID {
        @Override void validatePayable()    { throw new IllegalStateException("이미 결제 됨"); }
        @Override void validateCompletable() {}
    },

    // 배송까지 확정된 최종 상태
    CONFIRMED {
        @Override void validatePayable()  { throw new IllegalStateException("이미 확정된 주문입니다."); }
    },

    // 결제 실패 — 재시도 허용 (PENDING_PAYMENT로 복귀 후 재결제)
    PAYMENT_FAILED,

    // 취소 — 터미널 상태
    CANCELED {
        @Override void validatePayable()  { throw new IllegalStateException("취소된 주문은 결제할 수 없습니다."); }
        @Override void validateFailable() { throw new IllegalStateException("취소된 주문은 결제 실패 처리할 수 없습니다."); }
    };

    // 기본 구현: 허용(no-op). 각 상태가 Override해 금지 전이를 막는다.
    void validatePayable()     {}
    void validateCompletable() { throw new IllegalStateException("결제 완료 상태에서만 주문을 완료할 수 있습니다. 현재 상태: " + this); }
    void validateFailable()    { throw new IllegalStateException("결제 대기 상태에서만 결제 실패 처리가 가능합니다."); }
    void validateCancelable()  { throw new IllegalStateException("결제 대기 상태에서만 취소할 수 있습니다. 현재 상태: " + this); }
}



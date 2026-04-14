package allmart.orderservice.domain.order;

import allmart.orderservice.domain.AbstractEntity;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends AbstractEntity {

    private String tossOrderId;

    private Long buyerId;

    private OrderPayMethod payMethod;

    private List<OrderLine> orderLines;

    private List<ChargeLine> chargeLines = new ArrayList<>();

    private OrderStatus status;

    /** 주문 당시 배송지 스냅샷 — 수령인/전화번호 제외 */
    private DeliverySnapshot deliverySnapshot;

    /** 주문 당시 마트 정보 스냅샷 */
    private MartSnapshot martSnapshot;

    /** 주문/배달 요청사항 (선택) */
    private OrderMemo orderMemo;

    /** 배달료 (VAT 포함) */
    private Money deliveryFee;

    /** 배달료 공급가액 */
    private Money deliverySupply;

    /** 배달료 부가세 */
    private Money deliveryVat;

    private Boolean freeDelivery;

    private Money totalAmount;

    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime canceledAt;

    /**
     * 주문 생성.
     * 후불 결제(CASH_ON_DELIVERY, CARD_ON_DELIVERY)는 PAID로 초기화, 나머지는 PENDING_PAYMENT.
     * 배달료는 MartDeliveryConfig 정책이 결정한다.
     */
    public static Order create(OrderCreateRequest req, MartDeliveryConfig deliveryConfig) {
        // 검증 먼저 — Order 객체 생성 전에 실패해야 반쯤 초기화된 객체가 생기지 않음
        Money productTotal = calculateProductTotal(req.orderLines());

        Order order = new Order();

        order.initSnapshot(req);
        order.initStatus(req.payMethod());

        Money deliveryFee = deliveryConfig.calculateFee(productTotal);
        order.freeDelivery = deliveryFee.amount() == 0;
        order.applyDeliveryFee(deliveryFee);
        order.totalAmount = productTotal.plus(order.deliveryFee);
        order.initChargeLines(productTotal);

        return order;
    }

    /** 요청 스냅샷 필드 초기화 — 주문자, 결제수단, 상품/배송지/마트 정보 */
    private void initSnapshot(OrderCreateRequest req) {
        this.buyerId          = req.buyerId();
        this.payMethod        = req.payMethod();
        this.orderLines       = List.copyOf(req.orderLines());
        this.deliverySnapshot = req.deliverySnapshot();
        this.martSnapshot     = req.martSnapshot();
        this.orderMemo        = req.orderMemo();
    }

    /** 상태·시간·tossOrderId 초기화 — 후불 결제는 PAID, 나머지는 PENDING_PAYMENT */
    private void initStatus(OrderPayMethod payMethod) {
        boolean isOnDelivery = payMethod.isOnDeliveryPayment();
        this.status      = isOnDelivery ? OrderStatus.PAID : OrderStatus.PENDING_PAYMENT;
        this.createdAt   = LocalDateTime.now();
        this.paidAt      = null; // 후불: markAsCompleted() 시점에 기록 / 카드: markAsPaid() 시점에 기록
        this.tossOrderId = generateTossOrderId();
    }

    /** 상품 소계 계산 — 0원이면 주문 생성 불가 */
    private static Money calculateProductTotal(List<OrderLine> orderLines) {
        Money total = orderLines.stream()
                .map(OrderLine::lineAmount)
                .reduce(Money.zero(), Money::plus);
        if (total.amount() == 0) throw new IllegalArgumentException("totalAmount is zero");
        return total;
    }

    /**
     * 결제 승인 완료 (PENDING_PAYMENT → PAID).
     * 결제금액과 주문금액 불일치 시 예외 — 금액 조작 방지.
     * 이미 PAID면 중복 Kafka 메시지로 간주하고 무시(멱등).
     */
    public void markAsPaid(Money confirmedAmount) {
        if (this.status == OrderStatus.PAID) return;
        status.validatePayable();
        if (!this.totalAmount.equals(confirmedAmount)) {
            throw new IllegalArgumentException(
                    "결제 금액 불일치: 주문금액=" + this.totalAmount.amount() + ", 결제금액=" + confirmedAmount.amount());
        }
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    /**
     * 배달 완료 → 주문 확정 (PAID → CONFIRMED).
     * 후불 결제는 배달 완료 시점이 실제 결제 시점이므로 paidAt을 이 시점에 기록.
     * 이미 CONFIRMED면 중복 delivery.completed.v1로 간주하고 무시(멱등).
     */
    public void markAsCompleted() {
        if (this.status == OrderStatus.CONFIRMED) return;
        status.validateCompletable();
        if (this.payMethod.isOnDeliveryPayment()) this.paidAt = LocalDateTime.now();
        this.status      = OrderStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * 결제 실패 (PENDING_PAYMENT → PAYMENT_FAILED).
     * 이미 PAID면 PAID 상태를 보호하고 무시(멱등).
     */
    public void markPaymentFailed() {
        if (this.status == OrderStatus.PAYMENT_FAILED || this.status == OrderStatus.PAID) return;
        status.validateFailable();
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    /**
     * 주문 취소 (PENDING_PAYMENT → CANCELED).
     * 본인 주문 여부를 검증. 취소 후 order.canceled.v1 발행 → inventory-service 재고 해제.
     * 이미 CANCELED면 중복 요청으로 간주하고 무시(멱등).
     */
    public void cancel(Long requesterId) {
        if (!this.buyerId.equals(requesterId))
            throw new IllegalStateException("본인의 주문만 취소할 수 있습니다.");
        if (this.status == OrderStatus.CANCELED) return;
        status.validateCancelable();
        this.status     = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    /**
     * 시스템 취소 (재고 부족 등) — buyerId 검증 없음.
     * 후불 결제(PAID 상태)도 포함. 터미널 상태(CANCELED, CONFIRMED)는 무시(멱등).
     */
    public void cancelBySystem() {
        if (this.status == OrderStatus.CANCELED || this.status == OrderStatus.CONFIRMED) return;
        this.status     = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    /**
     * 재결제 요청 (PAYMENT_FAILED → PENDING_PAYMENT).
     * 본인 주문 여부를 검증. tossOrderId를 재발급해 결제창 재진입을 허용.
     */
    public void retryPayment(Long requesterId) {
        if (!this.buyerId.equals(requesterId))
            throw new IllegalStateException("본인의 주문만 재결제할 수 있습니다.");
        if (this.status != OrderStatus.PAYMENT_FAILED)
            throw new IllegalStateException("결제 실패 상태에서만 재결제가 가능합니다.");
        this.status   = OrderStatus.PENDING_PAYMENT;
        this.tossOrderId = generateTossOrderId();
    }

    /**
     * tossOrderId 발급. ORD_{timestamp}_{uuid 앞 8자리}.
     * TODO: 결제 게이트웨이 교체 시 도메인 코드 수정 필요 — 기술 부채.
     */
    private static String generateTossOrderId() {
        return "ORD_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 배달료·공급가액·부가세 저장.
     * 공급가액 = 배달료 ÷ VAT_DIVISOR(1.1), 부가세 = 배달료 - 공급가액.
     */
    private void applyDeliveryFee(Money fee) {
        this.deliveryFee    = fee;
        this.deliverySupply = fee.divide(ChargeType.VAT_DIVISOR);
        this.deliveryVat    = fee.minus(this.deliverySupply);
    }

    /**
     * 금액 명세(chargeLines) 초기화. create() 내부에서 1회 호출.
     */
    private void initChargeLines(Money productTotal) {
        this.chargeLines = new ArrayList<>(List.of(
                new ChargeLine(ChargeType.SUBTOTAL,        productTotal),
                new ChargeLine(ChargeType.DELIVERY_FEE,    this.deliveryFee),
                new ChargeLine(ChargeType.DELIVERY_SUPPLY, this.deliverySupply),
                new ChargeLine(ChargeType.DELIVERY_VAT,    this.deliveryVat)
        ));
    }
}

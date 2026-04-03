package allmart.orderservice.domain.order;

import allmart.orderservice.domain.AbstractEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@ToString(callSuper = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_buyer_id", columnList = "buyer_id"),
                @Index(name = "idx_orders_created_at", columnList = "created_at")
        }
)
public class Order extends AbstractEntity {

    @Column(name = "toss_order_id", nullable = false, length = 64, unique = true, updatable = false)
    private String tossOrderId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method", nullable = false, length = 20)
    private OrderPayMethod payMethod;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderLine> orderLines;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "charge_lines", joinColumns = @JoinColumn(name = "order_id"))
    private List<ChargeLine> chargeLines = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    // 주문 당시 배송지 스냅샷 — 수령인/전화번호 제외 (DB에서 직접 확인)
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "zipCode",       column = @Column(name = "zip_code",       nullable = false, length = 10)),
            @AttributeOverride(name = "roadAddress",   column = @Column(name = "road_address",   nullable = false, length = 200)),
            @AttributeOverride(name = "detailAddress", column = @Column(name = "detail_address", nullable = false, length = 200))
    })
    private DeliverySnapshot deliverySnapshot;

    // 주문 당시 마트 정보 스냅샷
    @Embedded
    private MartSnapshot martSnapshot;

    // 주문/배달 요청사항 (선택)
    @Embedded
    private OrderMemo orderMemo;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    public static Order create(OrderCreateRequest req) {
        Order order = new Order();

        order.buyerId          = req.buyerId();
        order.payMethod        = req.payMethod();
        order.orderLines       = List.copyOf(req.orderLines());
        order.deliverySnapshot = req.deliverySnapshot();
        order.martSnapshot     = req.martSnapshot();
        order.orderMemo        = req.orderMemo();
        // 후불 현금은 배달 완료 시 결제 — 배송 바로 시작을 위해 PAID로 초기화
        order.status    = (req.payMethod() == OrderPayMethod.CASH_ON_DELIVERY)
                ? OrderStatus.PAID : OrderStatus.PENDING_PAYMENT;
        order.createdAt = LocalDateTime.now();
        if (req.payMethod() == OrderPayMethod.CASH_ON_DELIVERY) {
            order.paidAt = LocalDateTime.now(); // 실제 결제는 배달 완료 시지만 배송 트리거용
        }
        order.tossOrderId      = "ORD_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);

        Money productTotal = req.orderLines().stream()
                .map(OrderLine::lineAmount)
                .reduce(Money.zero(), Money::plus);

        if (productTotal.amount() == 0) throw new IllegalArgumentException("totalAmount is zero");

        // 배송비: 5만 원 이상 무료, 미만 3,000원 — 백엔드가 금액의 단일 진실 출처
        Money deliveryFee = productTotal.amount() >= 50_000 ? Money.zero() : Money.of(3_000);

        order.totalAmount = productTotal.plus(deliveryFee);

        // 요금 명세 저장
        order.chargeLines.add(new ChargeLine(ChargeType.SUBTOTAL, productTotal));
        order.chargeLines.add(new ChargeLine(ChargeType.DELIVERY_FEE, deliveryFee));

        return order;
    }

    public void markAsPaid(long confirmedAmount) {
        if (this.status == OrderStatus.PAID) return; // 멱등: 중복 메시지 무시
        status.validatePayable();
        if (this.totalAmount.amount() != confirmedAmount) {
            throw new IllegalArgumentException(
                    "결제 금액 불일치: 주문금액=" + this.totalAmount.amount() + ", 결제금액=" + confirmedAmount);
        }
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void markAsCompleted() {
        if (this.status == OrderStatus.CONFIRMED) return; // 멱등: 중복 메시지 무시
        if (this.status != OrderStatus.PAID)
            throw new IllegalStateException("결제 완료 상태에서만 주문을 완료할 수 있습니다. 현재 상태: " + this.status);
        // 후불 현금: 배달 완료 시점이 결제 완료 시점
        if (this.payMethod == OrderPayMethod.CASH_ON_DELIVERY) {
            this.paidAt = LocalDateTime.now();
        }
        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    public void markPaymentFailed() {
        if (this.status == OrderStatus.PAYMENT_FAILED) return; // 멱등
        if (this.status == OrderStatus.PAID) return;           // PAID는 실패로 덮어쓰지 않음
        if (this.status != OrderStatus.PENDING_PAYMENT)
            throw new IllegalStateException("결제 대기 상태에서만 결제 실패 처리가 가능합니다.");
        this.status = OrderStatus.PAYMENT_FAILED;
    }

    /** 현금 선불 — 판매자가 현금 수령 확인 후 호출 */
    public void confirmCashPayment() {
        if (this.status == OrderStatus.PAID) return; // 멱등
        if (this.payMethod != OrderPayMethod.CASH_PREPAID)
            throw new IllegalStateException("현금 선불 결제 수단만 사용 가능합니다.");
        if (this.status != OrderStatus.PENDING_PAYMENT)
            throw new IllegalStateException("결제 대기 상태에서만 현금 확인이 가능합니다.");
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    /** 결제 실패 후 재결제 — PAYMENT_FAILED → PENDING_PAYMENT, tossOrderId 재발급 */
    public void retryPayment() {
        if (this.status != OrderStatus.PAYMENT_FAILED)
            throw new IllegalStateException("결제 실패 상태에서만 재결제가 가능합니다.");
        this.status = OrderStatus.PENDING_PAYMENT;
        this.tossOrderId = "ORD_" + System.currentTimeMillis() + "_" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}

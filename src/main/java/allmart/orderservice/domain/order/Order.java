package allmart.orderservice.domain.order;

import allmart.orderservice.domain.AbstractEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@ToString (callSuper = true)
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

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "order_lines",
            joinColumns = @JoinColumn(name = "order_id")
    )
    private List<OrderLine> orderLines;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "receiverName", column = @Column(name = "receiver_name", nullable = false, length = 50)),
            @AttributeOverride(name = "phone", column = @Column(name = "receiver_phone", nullable = false, length = 20)),
            @AttributeOverride(name = "memo", column = @Column(name = "delivery_memo", length = 200))
    })
    private ShippingInfo shippingInfo;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmAt;

    @Column(name = "canceled_at")
    private LocalDateTime cancelAt;

    public static Order create(OrderCreateRequest orderCreateRequest) {

        Order order = new Order();

        order.buyerId = orderCreateRequest.buyerId();
        order.orderLines = orderCreateRequest.orderLines();
        order.shippingInfo = orderCreateRequest.shippingInfo();

        order.status = OrderStatus.PENDING_PAYMENT;
        order.createdAt = LocalDateTime.now();
        order.tossOrderId = "ORD_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);

        Money productTotal = orderCreateRequest.orderLines().stream()
                .map(OrderLine::lineAmount)
                .reduce(Money.zero(), Money::plus);

        if (productTotal.amount() == 0) throw new IllegalArgumentException("totalAmount is zero");

        // 배송비: 5만 원 이상이면 무료, 미만이면 3,000원 — 백엔드가 금액의 단일 진실 출처
        Money shippingFee = productTotal.amount() >= 50_000 ? Money.zero() : Money.of(3_000);
        order.totalAmount = productTotal.plus(shippingFee);

        return order;
    }

    public void markAsPaid(long confirmedAmount) {
        if (this.status == OrderStatus.PAID) return; // 멱등: 중복 메시지 무시
        status.validatePayable();
        if (this.totalAmount.amount() != confirmedAmount) {
            throw new IllegalArgumentException(
                    "결제 금액 불일치: 주문금액=" + this.totalAmount.amount() + ", 결제금액=" + confirmedAmount
            );
        }
        this.status = OrderStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    public void markAsCompleted() {
        if (this.status == OrderStatus.CONFIRMED) return; // 멱등: 중복 메시지 무시
        if (this.status != OrderStatus.PAID) {
            throw new IllegalStateException("결제 완료 상태에서만 주문을 완료할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = OrderStatus.CONFIRMED;
        this.confirmAt = LocalDateTime.now();
    }

    public void markPaymentFailed() {
        if (this.status == OrderStatus.PAYMENT_FAILED) return; // 멱등: 중복 메시지 무시
        if (this.status == OrderStatus.PAID) return;           // PAID는 실패로 덮어쓰지 않음
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 상태에서만 결제 실패 처리가 가능합니다.");
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

}

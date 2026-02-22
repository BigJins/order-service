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
            @AttributeOverride(name = "memo", column = @Column(name = "shipping_memo", length = 200))
    })
    private ShippingInfo shippingInfo;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "total_amount", nullable = false))
    private Money totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

        order.totalAmount = orderCreateRequest.orderLines().stream()
                .map(OrderLine::lineAmount)
                .reduce(Money.zero(), Money::plus);

        if (order.totalAmount.amount() == 0) throw new IllegalArgumentException("totalAmount is zero");

        return order;
    }

    public void markAsPaid() {
        status.validatePayable();
        this.status = OrderStatus.PAID;
    }

    public void markPaymentFailed() {
        // 결제 대기에서만 실패로 갈 수 있게(원하면 규칙 조정 가능)
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("결제 대기 상태에서만 결제 실패 처리가 가능합니다.");
        }
        this.status = OrderStatus.PAYMENT_FAILED;
    }

}

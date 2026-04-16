package allmart.orderservice.domain.order.document;

import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderMemo;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MongoDB 역정규화 단일 도큐먼트 — CQRS 읽기 모델.
 * MySQL Order(쓰기)와 별도로 유지. AFTER_COMMIT 핸들러가 동기화.
 * statusHistory는 append-only (배민 WOOWACON 2023 패턴).
 */
@Document(collection = "orders")
public class OrderDocument {

    @Id
    private Long orderId;
    private String tossOrderId;
    private Long buyerId;
    private String payMethod;
    private String status;
    private long totalAmount;

    private List<OrderLineDoc> orderLines;
    private List<ChargeLineDoc> chargeLines;
    private DeliverySnapshotDoc deliverySnapshot;
    private MartSnapshotDoc martSnapshot;
    private OrderMemoDoc orderMemo;

    /** append-only 상태 이력 — 상태 전이마다 push, 절대 삭제 불가 */
    private List<StatusHistoryEntry> orderStatusHistory;

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime canceledAt;

    protected OrderDocument() {}

    /** MySQL Order → MongoDB 도큐먼트 변환 (최초 INSERT용) */
    public static OrderDocument from(Order order) {
        OrderDocument doc = new OrderDocument();
        doc.orderId      = order.getId();
        doc.tossOrderId  = order.getTossOrderId();
        doc.buyerId      = order.getBuyerId();
        doc.payMethod    = order.getPayMethod().name();
        doc.status       = order.getStatus().name();
        doc.totalAmount  = order.getTotalAmount().amount();

        doc.orderLines = order.getOrderLines().stream()
                .map(ol -> new OrderLineDoc(
                        ol.productId(),
                        ol.productNameSnapshot(),
                        ol.unitPrice().amount(),
                        ol.quantity(),
                        ol.lineAmount().amount()))
                .toList();

        doc.chargeLines = order.getChargeLines().stream()
                .map(cl -> new ChargeLineDoc(cl.type().name(), cl.amount().amount()))
                .toList();

        var ds = order.getDeliverySnapshot();
        doc.deliverySnapshot = new DeliverySnapshotDoc(ds.zipCode(), ds.roadAddress(), ds.detailAddress());

        var ms = order.getMartSnapshot();
        doc.martSnapshot = new MartSnapshotDoc(ms.martId(), ms.martName(), ms.martPhone());

        OrderMemo memo = order.getOrderMemo();
        doc.orderMemo = memo != null
                ? new OrderMemoDoc(memo.orderRequest(), memo.deliveryRequest())
                : null;

        doc.orderStatusHistory = new ArrayList<>();
        doc.orderStatusHistory.add(new StatusHistoryEntry(order.getStatus().name(), order.getCreatedAt()));

        doc.createdAt   = order.getCreatedAt();
        doc.paidAt      = order.getPaidAt();
        doc.confirmedAt = order.getConfirmedAt();
        doc.canceledAt  = order.getCanceledAt();

        return doc;
    }

    public void applyPaid(LocalDateTime paidAt) {
        this.status = "PAID";
        this.paidAt = paidAt;
        this.orderStatusHistory.add(new StatusHistoryEntry("PAID", paidAt));
    }

    public void applyFailed(LocalDateTime at) {
        this.status = "PAYMENT_FAILED";
        this.orderStatusHistory.add(new StatusHistoryEntry("PAYMENT_FAILED", at));
    }

    public void applyConfirmed(LocalDateTime at) {
        this.status       = "CONFIRMED";
        this.confirmedAt  = at;
        this.orderStatusHistory.add(new StatusHistoryEntry("CONFIRMED", at));
    }

    public void applyCanceled(LocalDateTime at) {
        this.status     = "CANCELED";
        this.canceledAt = at;
        this.orderStatusHistory.add(new StatusHistoryEntry("CANCELED", at));
    }

    // ── Getters (읽기 전용 — setter 없음) ─────────────────────────────

    public Long getOrderId()                              { return orderId; }
    public String getTossOrderId()                        { return tossOrderId; }
    public Long getBuyerId()                              { return buyerId; }
    public String getPayMethod()                          { return payMethod; }
    public String getStatus()                             { return status; }
    public long getTotalAmount()                          { return totalAmount; }
    public List<OrderLineDoc> getOrderLines()             { return Collections.unmodifiableList(orderLines); }
    public List<ChargeLineDoc> getChargeLines()           { return Collections.unmodifiableList(chargeLines); }
    public DeliverySnapshotDoc getDeliverySnapshot()      { return deliverySnapshot; }
    public MartSnapshotDoc getMartSnapshot()              { return martSnapshot; }
    public OrderMemoDoc getOrderMemo()                    { return orderMemo; }
    public List<StatusHistoryEntry> getOrderStatusHistory() { return Collections.unmodifiableList(orderStatusHistory); }
    public LocalDateTime getCreatedAt()                   { return createdAt; }
    public LocalDateTime getPaidAt()                      { return paidAt; }
    public LocalDateTime getConfirmedAt()                 { return confirmedAt; }
    public LocalDateTime getCanceledAt()                  { return canceledAt; }
}

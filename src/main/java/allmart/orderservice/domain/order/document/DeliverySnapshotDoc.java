package allmart.orderservice.domain.order.document;

/** 배송지 스냅샷 — OrderDocument에 역정규화 embed */
public record DeliverySnapshotDoc(
        String zipCode,
        String roadAddress,
        String detailAddress
) {}

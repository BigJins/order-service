package allmart.orderservice.domain.order.document;

/** 마트 정보 스냅샷 — OrderDocument에 역정규화 embed */
public record MartSnapshotDoc(
        Long martId,
        String martName,
        String martPhone
) {}

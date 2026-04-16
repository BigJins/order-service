package allmart.orderservice.domain.order.document;

/** 요금 명세 항목 — OrderDocument에 역정규화 embed */
public record ChargeLineDoc(
        String chargeType,
        long amount
) {}

package allmart.orderservice.adapter.webapi.dto;

import allmart.orderservice.domain.order.MartDeliveryConfig;

/** 마트 배달 설정 응답 DTO */
public record MartDeliveryConfigResponse(
        Long martId,
        long deliveryFeeAmount,
        long freeDeliveryThreshold
) {
    public static MartDeliveryConfigResponse from(MartDeliveryConfig config) {
        return new MartDeliveryConfigResponse(
                config.getMartId(),
                config.getDeliveryFee().amount(),
                config.getFreeDeliveryThreshold().amount()
        );
    }
}

package allmart.orderservice.adapter.webapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 마트 배달 설정 등록/수정 요청 DTO */
public record MartDeliveryConfigRequest(
        @NotNull @Min(0) Long deliveryFeeAmount,
        @NotNull @Min(0) Long freeDeliveryThreshold
) {}

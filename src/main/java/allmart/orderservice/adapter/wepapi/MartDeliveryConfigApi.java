package allmart.orderservice.adapter.wepapi;

import allmart.orderservice.application.MartDeliveryConfigService;
import allmart.orderservice.domain.order.MartDeliveryConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/marts/delivery-config")
@RequiredArgsConstructor
public class MartDeliveryConfigApi {

    private final MartDeliveryConfigService martDeliveryConfigService;

    /**
     * 배달 설정 등록 — 판매자 전용 (X-User-Type: MEMBER)
     * martId = X-User-Id (Gateway가 JWT에서 주입)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MartDeliveryConfigResponse create(
            @RequestHeader("X-User-Id") Long martId,
            @RequestBody @Valid MartDeliveryConfigRequest req) {
        MartDeliveryConfig config = martDeliveryConfigService.create(martId, req.deliveryFeeAmount(), req.freeDeliveryThreshold());
        return MartDeliveryConfigResponse.from(config);
    }

    /**
     * 배달 설정 수정 — 판매자 전용 (X-User-Type: MEMBER)
     */
    @PatchMapping
    public MartDeliveryConfigResponse update(
            @RequestHeader("X-User-Id") Long martId,
            @RequestBody @Valid MartDeliveryConfigRequest req) {
        MartDeliveryConfig config = martDeliveryConfigService.update(martId, req.deliveryFeeAmount(), req.freeDeliveryThreshold());
        return MartDeliveryConfigResponse.from(config);
    }

    public record MartDeliveryConfigRequest(
            @NotNull @Min(0) Long deliveryFeeAmount,
            @NotNull @Min(0) Long freeDeliveryThreshold
    ) {}

    public record MartDeliveryConfigResponse(
            Long martId,
            long deliveryFeeAmount,
            long freeDeliveryThreshold
    ) {
        static MartDeliveryConfigResponse from(MartDeliveryConfig config) {
            return new MartDeliveryConfigResponse(
                    config.getMartId(),
                    config.getDeliveryFeeAmount(),
                    config.getFreeDeliveryThreshold()
            );
        }
    }
}

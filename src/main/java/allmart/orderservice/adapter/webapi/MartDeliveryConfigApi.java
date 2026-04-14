package allmart.orderservice.adapter.webapi;

import allmart.orderservice.adapter.webapi.dto.MartDeliveryConfigRequest;
import allmart.orderservice.adapter.webapi.dto.MartDeliveryConfigResponse;
import allmart.orderservice.application.MartDeliveryConfigService;
import allmart.orderservice.domain.order.Money;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 마트 배달 설정 REST API — 판매자 전용 (X-User-Type: MEMBER). martId = X-User-Id (Gateway 주입). */
@RestController
@RequestMapping("/api/marts/delivery-config")
@RequiredArgsConstructor
public class MartDeliveryConfigApi {

    private final MartDeliveryConfigService martDeliveryConfigService;

    /** 배달 설정 조회 — 없으면 404 */
    @GetMapping
    public ResponseEntity<MartDeliveryConfigResponse> get(
            @RequestHeader("X-User-Id") Long martId) {
        return martDeliveryConfigService.find(martId)
                .map(MartDeliveryConfigResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 배달 설정 등록/수정 (upsert) — 판매자 전용 (X-User-Type: MEMBER)
     * martId = X-User-Id (Gateway가 JWT에서 주입)
     */
    @PostMapping
    public ResponseEntity<MartDeliveryConfigResponse> upsert(
            @RequestHeader("X-User-Id") Long martId,
            @RequestBody @Valid MartDeliveryConfigRequest req) {
        MartDeliveryConfigResponse body = MartDeliveryConfigResponse.from(
                martDeliveryConfigService.upsert(
                        martId, Money.of(req.deliveryFeeAmount()), Money.of(req.freeDeliveryThreshold())));
        return ResponseEntity.ok(body);
    }

    /**
     * 배달 설정 등록 — 판매자 전용 (X-User-Type: MEMBER)
     * @deprecated POST가 upsert로 변경됨. 하위 호환 유지용.
     */
    @Deprecated
    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<MartDeliveryConfigResponse> create(
            @RequestHeader("X-User-Id") Long martId,
            @RequestBody @Valid MartDeliveryConfigRequest req) {
        MartDeliveryConfigResponse body = MartDeliveryConfigResponse.from(
                martDeliveryConfigService.create(
                        martId, Money.of(req.deliveryFeeAmount()), Money.of(req.freeDeliveryThreshold())));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /** 배달 설정 수정 — 판매자 전용 (X-User-Type: MEMBER) */
    @PatchMapping
    public ResponseEntity<MartDeliveryConfigResponse> update(
            @RequestHeader("X-User-Id") Long martId,
            @RequestBody @Valid MartDeliveryConfigRequest req) {
        MartDeliveryConfigResponse body = MartDeliveryConfigResponse.from(
                martDeliveryConfigService.update(
                        martId, Money.of(req.deliveryFeeAmount()), Money.of(req.freeDeliveryThreshold())));
        return ResponseEntity.ok(body);
    }
}

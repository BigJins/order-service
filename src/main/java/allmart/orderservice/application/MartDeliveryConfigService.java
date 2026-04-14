package allmart.orderservice.application;

import allmart.orderservice.application.required.MartDeliveryConfigRepository;
import allmart.orderservice.domain.order.MartDeliveryConfig;
import allmart.orderservice.domain.order.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
/** 마트 배달 설정 CRUD 유스케이스. */
public class MartDeliveryConfigService {

    private final MartDeliveryConfigRepository martDeliveryConfigRepository;

    /** 마트 배달 설정 조회 */
    @Transactional(readOnly = true)
    public Optional<MartDeliveryConfig> find(Long martId) {
        return martDeliveryConfigRepository.findByMartId(martId);
    }

    /** 배달 설정 최초 등록 — 이미 존재하면 예외 */
    public MartDeliveryConfig create(Long martId, Money deliveryFee, Money freeDeliveryThreshold) {
        if (martDeliveryConfigRepository.findByMartId(martId).isPresent()) {
            throw new IllegalStateException("이미 배달 설정이 존재합니다. 수정은 PATCH를 사용하세요. martId=" + martId);
        }
        return martDeliveryConfigRepository.save(
                MartDeliveryConfig.create(martId, deliveryFee, freeDeliveryThreshold));
    }

    /** 배달 설정 수정 — 존재하지 않으면 예외 */
    public MartDeliveryConfig update(Long martId, Money deliveryFee, Money freeDeliveryThreshold) {
        MartDeliveryConfig config = martDeliveryConfigRepository.findByMartId(martId)
                .orElseThrow(() -> new IllegalArgumentException("배달 설정이 존재하지 않습니다. 먼저 등록하세요. martId=" + martId));
        config.update(deliveryFee, freeDeliveryThreshold);
        return config;
    }

    /** 배달 설정 등록/수정 (upsert) — find 1회로 존재 여부 판단 */
    public MartDeliveryConfig upsert(Long martId, Money deliveryFee, Money freeDeliveryThreshold) {
        return martDeliveryConfigRepository.findByMartId(martId)
                .map(config -> { config.update(deliveryFee, freeDeliveryThreshold); return config; })
                .orElseGet(() -> martDeliveryConfigRepository.save(
                        MartDeliveryConfig.create(martId, deliveryFee, freeDeliveryThreshold)));
    }
}

package allmart.orderservice.application;

import allmart.orderservice.application.required.MartDeliveryConfigRepository;
import allmart.orderservice.domain.order.MartDeliveryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MartDeliveryConfigService {

    private final MartDeliveryConfigRepository martDeliveryConfigRepository;

    public MartDeliveryConfig create(Long martId, long deliveryFeeAmount, long freeDeliveryThreshold) {
        if (martDeliveryConfigRepository.findByMartId(martId).isPresent()) {
            throw new IllegalStateException("이미 배달 설정이 존재합니다. 수정은 PATCH를 사용하세요. martId=" + martId);
        }
        return martDeliveryConfigRepository.save(
                MartDeliveryConfig.create(martId, deliveryFeeAmount, freeDeliveryThreshold));
    }

    public MartDeliveryConfig update(Long martId, long deliveryFeeAmount, long freeDeliveryThreshold) {
        MartDeliveryConfig config = martDeliveryConfigRepository.findByMartId(martId)
                .orElseThrow(() -> new IllegalArgumentException("배달 설정이 존재하지 않습니다. 먼저 등록하세요. martId=" + martId));
        config.update(deliveryFeeAmount, freeDeliveryThreshold);
        return config;
    }
}

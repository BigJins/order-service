package allmart.orderservice.application.required;

import allmart.orderservice.domain.order.MartDeliveryConfig;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/** 마트 배달 설정 저장소 포트 */
public interface MartDeliveryConfigRepository extends Repository<MartDeliveryConfig, Long> {
    MartDeliveryConfig save(MartDeliveryConfig config);
    /** martId로 배달 설정 조회 */
    Optional<MartDeliveryConfig> findByMartId(Long martId);
}

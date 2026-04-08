package allmart.orderservice.application.required;

import allmart.orderservice.domain.order.MartDeliveryConfig;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface MartDeliveryConfigRepository extends Repository<MartDeliveryConfig, Long> {
    MartDeliveryConfig save(MartDeliveryConfig config);
    Optional<MartDeliveryConfig> findByMartId(Long martId);
}

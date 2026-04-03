package allmart.orderservice.adapter.client.stub;

import allmart.orderservice.adapter.client.InventoryServiceClient;
import allmart.orderservice.adapter.client.dto.InventoryReserveRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 부하 테스트 전용 stub — inventory-service 없이 order-service 단독 실행 가능.
 * reserve / confirm / release 모두 즉시 성공 반환 (재고 검사 없음).
 *
 * 활성화: --spring.profiles.active=local,stub
 */
@Component
@Profile("stub")
public class StubInventoryServiceClient extends InventoryServiceClient {

    public StubInventoryServiceClient() {
        super(null);  // RestClient 불필요 — HTTP 호출 없음
    }

    @Override
    public void reserve(InventoryReserveRequest request) {
        // 항상 재고 있음으로 처리
    }

    @Override
    public void confirm(String tossOrderId) {
        // 항상 성공
    }

    @Override
    public void release(String tossOrderId) {
        // 항상 성공
    }
}

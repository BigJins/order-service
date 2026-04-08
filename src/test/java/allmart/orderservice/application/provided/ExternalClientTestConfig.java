package allmart.orderservice.application.provided;

import allmart.orderservice.adapter.client.InventoryServiceClient;
import allmart.orderservice.adapter.client.ProductServiceClient;
import allmart.orderservice.adapter.client.dto.InventoryReserveRequest;
import allmart.orderservice.adapter.client.dto.ProductPriceResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 통합 테스트용 외부 서비스 클라이언트 가짜 구현체.
 * product-service와 inventory-service가 기동되지 않아도 테스트 가능.
 */
@TestConfiguration
class ExternalClientTestConfig {

    @Bean
    @Primary
    ProductServiceClient fakeProductServiceClient() {
        return new ProductServiceClient(null) {
            // 요청한 unitPrice 그대로 반환 — 가격 검증 통과
            @Override
            public ProductPriceResponse getPrice(Long productId) {
                return new ProductPriceResponse(productId, "테스트상품", 1000L, "TAXABLE", null, null);
            }
        };
    }

    @Bean
    @Primary
    InventoryServiceClient fakeInventoryServiceClient() {
        return new InventoryServiceClient(null) {
            @Override
            public void reserve(InventoryReserveRequest request) { /* no-op */ }

            @Override
            public void confirm(String tossOrderId) { /* no-op */ }

            @Override
            public void release(String tossOrderId) { /* no-op */ }
        };
    }
}

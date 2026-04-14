package allmart.orderservice.application.provided;

import allmart.orderservice.application.required.InventoryPort;
import allmart.orderservice.application.required.ProductPort;
import allmart.orderservice.domain.order.Money;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * 통합 테스트용 외부 서비스 포트 가짜 구현체.
 * product-service와 inventory-service가 기동되지 않아도 테스트 가능.
 */
@TestConfiguration
class ExternalClientTestConfig {

    @Bean
    @Primary
    ProductPort fakeProductPort() {
        return productId -> new ProductPort.ProductInfo(Money.of(1000L), "TAXABLE");
    }

    @Bean
    @Primary
    InventoryPort fakeInventoryPort() {
        return new InventoryPort() {
            @Override public void reserve(String tossOrderId, List<ReserveItem> items) { /* no-op */ }
            @Override public void confirm(String tossOrderId) { /* no-op */ }
            @Override public void release(String tossOrderId) { /* no-op */ }
        };
    }
}

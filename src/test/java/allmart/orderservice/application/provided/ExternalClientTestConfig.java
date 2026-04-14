package allmart.orderservice.application.provided;

import allmart.orderservice.application.required.ProductPort;
import allmart.orderservice.domain.order.Money;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 통합 테스트용 외부 서비스 포트 가짜 구현체.
 * product-service가 기동되지 않아도 테스트 가능.
 * InventoryPort 제거됨 — 재고 예약은 Kafka 비동기 전환으로 동기 포트 불필요.
 */
@TestConfiguration
class ExternalClientTestConfig {

    @Bean
    @Primary
    ProductPort fakeProductPort() {
        return productId -> new ProductPort.ProductInfo(Money.of(1000L), "TAXABLE");
    }
}

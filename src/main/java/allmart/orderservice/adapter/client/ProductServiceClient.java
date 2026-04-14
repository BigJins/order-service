package allmart.orderservice.adapter.client;

import allmart.orderservice.adapter.client.dto.ProductPriceResponse;
import allmart.orderservice.application.required.ProductPort;
import allmart.orderservice.domain.order.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * product-service 동기 HTTP 클라이언트. ProductPort 구현체.
 * 주문 생성 시 상품 가격을 검증하기 위해 호출한다.
 * 호출 실패 시 예외를 전파하여 주문 생성을 막는다 (가격 모르면 주문 불가).
 */
@Component
@RequiredArgsConstructor
public class ProductServiceClient implements ProductPort {

    private final RestClient productServiceRestClient;

    /** 상품 현재 가격·세금 유형 동기 조회 — 실패 시 예외 전파 → 주문 생성 중단 */
    @Override
    public ProductInfo getProductInfo(Long productId) {
        ProductPriceResponse resp = productServiceRestClient.get()
                .uri("/internal/products/{id}/price", productId)
                .retrieve()
                .body(ProductPriceResponse.class);
        return new ProductInfo(Money.of(resp.price()), resp.taxType());
    }
}

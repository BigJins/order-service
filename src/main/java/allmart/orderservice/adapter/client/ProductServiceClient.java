package allmart.orderservice.adapter.client;

import allmart.orderservice.adapter.client.dto.ProductPriceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * product-service 동기 HTTP 클라이언트.
 * 주문 생성 시 상품 가격을 검증하기 위해 호출한다.
 * 호출 실패 시 예외를 전파하여 주문 생성을 막는다 (가격 모르면 주문 불가).
 */
@Component
@RequiredArgsConstructor
public class ProductServiceClient {

    private final RestClient productServiceRestClient;

    public ProductPriceResponse getPrice(Long productId) {
        return productServiceRestClient.get()
                .uri("/internal/products/{id}/price", productId)
                .retrieve()
                .body(ProductPriceResponse.class);
    }
}

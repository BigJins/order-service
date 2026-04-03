package allmart.orderservice.adapter.client.stub;

import allmart.orderservice.adapter.client.ProductServiceClient;
import allmart.orderservice.adapter.client.dto.ProductPriceResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 부하 테스트 전용 stub — product-service 없이 order-service 단독 실행 가능.
 * stub 프로파일 활성화 시 실제 ProductServiceClient 대신 사용됨.
 *
 * 가격 테이블 (k6 스크립트의 unitPrice와 반드시 일치해야 함):
 *   productId 1 → 감귤    6,000원
 *   productId 2 → 딸기    7,300원
 *   productId 3 → 한라봉 15,000원
 *
 * 활성화: --spring.profiles.active=local,stub
 */
@Component
@Profile("stub")
public class StubProductServiceClient extends ProductServiceClient {

    private static final Map<Long, ProductPriceResponse> PRICE_TABLE = Map.of(
            1L, new ProductPriceResponse(1L, "감귤",    6_000L),
            2L, new ProductPriceResponse(2L, "딸기",    7_300L),
            3L, new ProductPriceResponse(3L, "한라봉", 15_000L)
    );

    public StubProductServiceClient() {
        super(null);  // RestClient 불필요 — HTTP 호출 없음
    }

    @Override
    public ProductPriceResponse getPrice(Long productId) {
        ProductPriceResponse response = PRICE_TABLE.get(productId);
        if (response == null) {
            throw new IllegalArgumentException("[stub] 알 수 없는 productId: " + productId
                    + " — k6 스크립트에서 productId 1, 2, 3만 사용하세요.");
        }
        return response;
    }
}

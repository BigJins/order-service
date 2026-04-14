package allmart.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 서비스 간 동기 HTTP 클라이언트 설정.
 * 연결 타임아웃 3초 / 읽기 타임아웃 5초 공통 적용.
 */
@Configuration
public class RestClientConfig {

    @Value("${internal.product-service.url:http://localhost:8083}")
    private String productServiceUrl;

    @Value("${internal.inventory-service.url:http://localhost:8085}")
    private String inventoryServiceUrl;

    /** product-service 전용 RestClient (baseUrl + 타임아웃) */
    @Bean
    public RestClient productServiceRestClient() {
        return RestClient.builder()
                .baseUrl(productServiceUrl)
                .requestFactory(requestFactory())
                .build();
    }

    /** inventory-service 전용 RestClient (baseUrl + 타임아웃) */
    @Bean
    public RestClient inventoryServiceRestClient() {
        return RestClient.builder()
                .baseUrl(inventoryServiceUrl)
                .requestFactory(requestFactory())
                .build();
    }

    /** 연결 3초 / 읽기 5초 타임아웃 공통 팩토리 */
    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}

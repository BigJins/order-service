package allmart.orderservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Value("${internal.product-service.url:http://localhost:8083}")
    private String productServiceUrl;

    @Value("${internal.inventory-service.url:http://localhost:8085}")
    private String inventoryServiceUrl;

    @Bean
    public RestClient productServiceRestClient() {
        return RestClient.builder()
                .baseUrl(productServiceUrl)
                .requestFactory(requestFactory())
                .build();
    }

    @Bean
    public RestClient inventoryServiceRestClient() {
        return RestClient.builder()
                .baseUrl(inventoryServiceUrl)
                .requestFactory(requestFactory())
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
}

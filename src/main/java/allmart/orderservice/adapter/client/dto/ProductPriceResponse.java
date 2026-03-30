package allmart.orderservice.adapter.client.dto;

/**
 * product-service GET /internal/products/{id}/price 응답 DTO
 */
public record ProductPriceResponse(
        Long productId,
        String productName,
        long price
) {}

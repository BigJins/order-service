package allmart.orderservice.adapter.client.dto;

/**
 * product-service GET /internal/products/{id}/price 응답 DTO
 */
public record ProductPriceResponse(
        Long productId,
        String productName,
        long price,
        String taxType,  // "TAXABLE" | "TAX_EXEMPT" | "PENDING"
        String unit,     // 판매 단위 예: "박스", "개" (nullable)
        Integer unitSize // 단위당 개수 예: 10 (nullable)
) {}

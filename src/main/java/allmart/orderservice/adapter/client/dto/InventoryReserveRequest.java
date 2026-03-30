package allmart.orderservice.adapter.client.dto;

import java.util.List;

/**
 * inventory-service POST /internal/inventory/reserve 요청 DTO
 */
public record InventoryReserveRequest(
        String tossOrderId,
        List<ReserveItem> items
) {
    public InventoryReserveRequest {
        items = List.copyOf(items);
    }

    public record ReserveItem(Long productId, int quantity) {}
}

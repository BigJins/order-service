package allmart.orderservice.adapter.client;

import allmart.orderservice.adapter.client.dto.InventoryReserveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * inventory-service 동기 HTTP 클라이언트.
 * 주문 생성 시 재고 예약, 결제 완료/실패 시 confirm/release 호출.
 */
@Component
@RequiredArgsConstructor
public class InventoryServiceClient {

    private final RestClient inventoryServiceRestClient;

    public void reserve(InventoryReserveRequest request) {
        inventoryServiceRestClient.post()
                .uri("/internal/inventory/reserve")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void confirm(String tossOrderId) {
        inventoryServiceRestClient.post()
                .uri("/internal/inventory/confirm/{tossOrderId}", tossOrderId)
                .retrieve()
                .toBodilessEntity();
    }

    public void release(String tossOrderId) {
        inventoryServiceRestClient.post()
                .uri("/internal/inventory/release/{tossOrderId}", tossOrderId)
                .retrieve()
                .toBodilessEntity();
    }
}

package allmart.orderservice.adapter.webapi;

import allmart.orderservice.adapter.webapi.dto.OrderCreatorResponse;
import allmart.orderservice.adapter.webapi.dto.OrderResponse;
import allmart.orderservice.application.provided.OrderCreator;
import allmart.orderservice.application.provided.OrderFinder;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderApi {

    private final OrderCreator orderCreator;
    private final OrderFinder orderFinder;

    /**
     * 주문 생성
     *
     * buyerId 우선순위: Gateway가 주입한 X-User-Id 헤더 > request body buyerId
     * - Gateway 경유 시: JWT에서 추출한 customerId가 X-User-Id 헤더로 전달됨 (클라이언트 조작 불가)
     * - 직접 호출(테스트/내부): request body buyerId 사용
     */
    @PostMapping("/api/orders")
    public OrderCreatorResponse create(
            @RequestBody @Valid OrderCreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long buyerIdFromGateway) {

        OrderCreateRequest effectiveRequest = buyerIdFromGateway != null
                ? new OrderCreateRequest(buyerIdFromGateway, request.orderLines(), request.shippingInfo())
                : request;

        Order order = orderCreator.create(effectiveRequest);
        return OrderCreatorResponse.of(order);
    }

    @GetMapping("/api/orders")
    public List<OrderResponse> findAll() {
        return orderFinder.findAll().stream().map(OrderResponse::of).toList();
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderResponse find(@PathVariable Long orderId) {
        return OrderResponse.of(orderFinder.findDetailById(orderId));
    }

}

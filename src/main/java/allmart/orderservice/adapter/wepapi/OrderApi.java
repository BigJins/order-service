package allmart.orderservice.adapter.wepapi;

import allmart.orderservice.adapter.wepapi.dto.OrderCreatorResponse;
import allmart.orderservice.adapter.wepapi.dto.OrderResponse;
import allmart.orderservice.application.provided.OrderCreator;
import allmart.orderservice.application.provided.OrderFinder;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequiredArgsConstructor
public class OrderApi {

    private final OrderCreator orderCreator;
    private final OrderFinder orderFinder;

    @PostMapping("/api/orders")
    public OrderCreatorResponse create(@RequestBody @Valid OrderCreateRequest request) {
        Order order = orderCreator.create(request);

        return OrderCreatorResponse.of(order);
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderResponse find(@PathVariable Long orderId) {

        return OrderResponse.of(orderFinder.find(orderId));
    }

}

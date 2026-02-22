package allmart.orderservice.domain.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record OrderCreateRequest(
        @NotNull(message = "buyerId must not be null")
        Long buyerId,

        @NotNull(message = "orderLines must not be null")
        @Size(min = 1, message = "orderLines must not be empty")
        @Valid
        List<OrderLine> orderLines,

        @NotNull(message = "shippingInfo must not be null")
        @Valid
        ShippingInfo shippingInfo)
{
    public OrderCreateRequest {
        requireNonNull(buyerId, "buyerId");
        requireNonNull(orderLines, "orderLines");
        requireNonNull(shippingInfo, "shippingInfo");

        if (orderLines.isEmpty()) throw new IllegalArgumentException("orderLines is empty");
        orderLines = List.copyOf(orderLines);
    }
}
package allmart.orderservice.domain.order;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

import static java.util.Objects.requireNonNull;

public record OrderCreateRequest(
        @NotNull(message = "buyerId must not be null")
        Long buyerId,

        @NotNull(message = "payMethod must not be null")
        OrderPayMethod payMethod,

        @NotNull(message = "orderLines must not be null")
        @Size(min = 1, message = "orderLines must not be empty")
        @Valid
        List<OrderLine> orderLines,

        @NotNull(message = "deliverySnapshot must not be null")
        @Valid
        DeliverySnapshot deliverySnapshot,

        @NotNull(message = "martSnapshot must not be null")
        @Valid
        MartSnapshot martSnapshot,

        @Nullable
        OrderMemo orderMemo
) {
    public OrderCreateRequest {
        requireNonNull(buyerId, "buyerId");
        requireNonNull(payMethod, "payMethod");
        requireNonNull(orderLines, "orderLines");
        requireNonNull(deliverySnapshot, "deliverySnapshot");
        requireNonNull(martSnapshot, "martSnapshot");

        if (orderLines.isEmpty()) throw new IllegalArgumentException("orderLines is empty");
        orderLines = List.copyOf(orderLines);
    }
}

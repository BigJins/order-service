package allmart.orderservice.adapter.webapi;

import allmart.orderservice.adapter.webapi.dto.OrderCreatorResponse;
import allmart.orderservice.application.provided.OrderCreator;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class OrderApi {

    private final OrderCreator orderCreator;

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
                ? new OrderCreateRequest(buyerIdFromGateway, request.payMethod(), request.orderLines(),
                        request.deliverySnapshot(), request.martSnapshot(), request.orderMemo())
                : request;

        Order order = orderCreator.create(effectiveRequest);
        return OrderCreatorResponse.of(order);
    }

    /** 현금 선불 — 판매자가 현금 수령 확인 (MEMBER 전용) */
    @PatchMapping("/api/orders/{orderId}/confirm-cash")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmCash(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Type", required = false) String userType) {
        if (!"MEMBER".equals(userType)) throw new IllegalStateException("판매자 권한이 필요합니다.");
        orderCreator.confirmCashPayment(orderId);
    }

    /** 결제 실패 후 재결제 요청 (CUSTOMER 전용) */
    @PatchMapping("/api/orders/{orderId}/retry-payment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retryPayment(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long buyerIdFromGateway) {
        if (buyerIdFromGateway == null) throw new IllegalStateException("인증 정보가 없습니다.");
        orderCreator.retryPayment(orderId, buyerIdFromGateway);
    }

}

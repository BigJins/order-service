package allmart.orderservice.adapter.webapi;

import allmart.orderservice.adapter.webapi.dto.OrderCreatorResponse;
import allmart.orderservice.application.provided.OrderCreator;
import allmart.orderservice.domain.order.Order;
import allmart.orderservice.domain.order.OrderCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 주문 REST API 컨트롤러 — 주문 생성·재결제·취소 (쓰기 전용, 조회는 order-query-service) */
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

        // Gateway 주입 헤더가 있으면 request body의 buyerId를 덮어씀 (클라이언트 조작 방지)
        OrderCreateRequest effectiveRequest = buyerIdFromGateway != null
                ? new OrderCreateRequest(buyerIdFromGateway, request.payMethod(), request.orderLines(),
                        request.deliverySnapshot(), request.martSnapshot(), request.orderMemo())
                : request;

        Order order = orderCreator.create(effectiveRequest);
        return OrderCreatorResponse.of(order);
    }

    /** 결제 실패 후 재결제 요청 (CUSTOMER 전용) */
    @PatchMapping("/api/orders/{orderId}/retry-payment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retryPayment(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long buyerIdFromGateway) {
        orderCreator.retryPayment(orderId, requireBuyerId(buyerIdFromGateway));
    }

    /**
     * 주문 취소 (CUSTOMER 전용, PENDING_PAYMENT 상태만 허용).
     * 취소 후 order.canceled.v1 Outbox → Debezium → inventory-service 재고 해제 (비동기)
     */
    @DeleteMapping("/api/orders/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long buyerIdFromGateway) {
        orderCreator.cancelOrder(orderId, requireBuyerId(buyerIdFromGateway));
    }

    /** X-User-Id 헤더 필수 검증 — null이면 403 */
    private Long requireBuyerId(Long buyerIdFromGateway) {
        if (buyerIdFromGateway == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "인증 정보가 없습니다.");
        return buyerIdFromGateway;
    }

}

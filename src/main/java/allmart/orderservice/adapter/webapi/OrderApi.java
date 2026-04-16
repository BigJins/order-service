package allmart.orderservice.adapter.webapi;

import allmart.orderservice.adapter.webapi.dto.OrderCreatorResponse;
import allmart.orderservice.adapter.webapi.dto.OrderDetailResponse;
import allmart.orderservice.application.command.OrderCommandUseCase;
import allmart.orderservice.application.query.OrderQueryUseCase;
import allmart.orderservice.domain.order.OrderCreateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** 주문 REST API 컨트롤러 — 주문 생성·재결제·취소(쓰기) + 주문 조회(읽기 — MongoDB) */
@RestController
@RequiredArgsConstructor
public class OrderApi {

    private final OrderCommandUseCase orderCommandUseCase;
    private final OrderQueryUseCase orderQueryUseCase;

    // ── Command (쓰기) ────────────────────────────────────────────────

    /**
     * 주문 생성.
     * buyerId 우선순위: Gateway가 주입한 X-User-Id 헤더 > request body buyerId
     */
    @PostMapping("/api/orders")
    public OrderCreatorResponse create(
            @RequestBody @Valid OrderCreateRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long buyerIdFromGateway) {

        OrderCreateRequest effectiveRequest = buyerIdFromGateway != null
                ? new OrderCreateRequest(buyerIdFromGateway, request.payMethod(), request.orderLines(),
                        request.deliverySnapshot(), request.martSnapshot(), request.orderMemo())
                : request;

        return OrderCreatorResponse.of(orderCommandUseCase.create(effectiveRequest));
    }

    /** 결제 실패 후 재결제 요청 (CUSTOMER 전용) */
    @PatchMapping("/api/orders/{orderId}/retry-payment")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retryPayment(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long buyerIdFromGateway) {
        orderCommandUseCase.retryPayment(orderId, requireBuyerId(buyerIdFromGateway));
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
        orderCommandUseCase.cancelOrder(orderId, requireBuyerId(buyerIdFromGateway));
    }

    // ── Query (읽기 — MongoDB) ─────────────────────────────────────────

    /** 주문 단건 상세 조회 (MongoDB 단일 도큐먼트) */
    @GetMapping("/api/orders/{orderId}")
    public OrderDetailResponse getOrder(@PathVariable Long orderId) {
        return orderQueryUseCase.findById(orderId);
    }

    /**
     * 주문 목록 조회 (페이징, 최신 순, MongoDB).
     * MEMBER(판매자) → 전체 주문 조회
     * CUSTOMER(구매자) → 본인 주문만 조회
     */
    @GetMapping("/api/orders")
    public Page<OrderDetailResponse> listOrders(
            @RequestHeader(value = "X-User-Id", required = false) Long buyerIdFromGateway,
            @RequestHeader(value = "X-User-Type", required = false) String userType,
            @PageableDefault(size = 100, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if ("MEMBER".equals(userType)) {
            return orderQueryUseCase.findAll(pageable);
        }
        return orderQueryUseCase.findByBuyer(requireBuyerId(buyerIdFromGateway), pageable);
    }

    /** X-User-Id 헤더 필수 검증 — null이면 403 */
    private Long requireBuyerId(Long buyerIdFromGateway) {
        if (buyerIdFromGateway == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "인증 정보가 없습니다.");
        return buyerIdFromGateway;
    }
}

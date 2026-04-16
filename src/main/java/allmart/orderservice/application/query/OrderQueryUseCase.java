package allmart.orderservice.application.query;

import allmart.orderservice.adapter.webapi.dto.OrderDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 주문 Query(읽기) 유스케이스 포트.
 * MongoDB OrderDocument 기반 단일 도큐먼트 조회 (JOIN 없음).
 * 구현체: OrderQueryService
 */
public interface OrderQueryUseCase {

    /** 주문 단건 조회 */
    OrderDetailResponse findById(Long orderId);

    /** 구매자별 주문 목록 (페이징, 최신 순) */
    Page<OrderDetailResponse> findByBuyer(Long buyerId, Pageable pageable);

    /** 전체 주문 목록 (판매자 전용, 페이징, 최신 순) */
    Page<OrderDetailResponse> findAll(Pageable pageable);
}

package allmart.orderservice.application.query;

import allmart.orderservice.domain.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 주문 Query(읽기) 유스케이스 포트.
 * order-query-service(MongoDB) 제거 후 order-service 내 DB Replica에서 직접 조회.
 * 구현체: OrderQueryService
 */
public interface OrderQueryUseCase {

    /** 주문 단건 조회 (orderLines fetch join 포함) */
    Order findById(Long orderId);

    /** 구매자별 주문 목록 (페이징, 최신 순) */
    Page<Order> findByBuyer(Long buyerId, Pageable pageable);

    /** 전체 주문 목록 (판매자 전용, 페이징, 최신 순) */
    Page<Order> findAll(Pageable pageable);
}

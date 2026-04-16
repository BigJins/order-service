package allmart.orderservice.application.required;

import allmart.orderservice.domain.order.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 주문 정보를 저장하거나 조회한다.
 * Command: save, findByTossOrderId, findDetailById
 * Query:   findPageByBuyerIdOrderByCreatedAtDesc (Replica 라우팅 대상)
 */
public interface OrderRepository extends Repository<Order, Long> {

    Order save(Order order);

    /** tossOrderId로 주문 단순 조회 (Kafka 결제 결과 처리 시 사용) */
    Optional<Order> findByTossOrderId(String tossOrderId);

    /** orderId로 orderLines fetch join 조회 — N+1 방지 */
    @Query("""
          select distinct o
          from Order o
          left join fetch o.orderLines
          where o.id = :orderId
          """)
    Optional<Order> findDetailById(@Param("orderId") Long orderId);

    /**
     * 구매자별 주문 목록 페이징 조회 (orderLines fetch join, 최신 순).
     * @Transactional(readOnly=true) → ReplicationRoutingDataSource가 Replica로 라우팅.
     */
    @Query(value = """
          select distinct o
          from Order o
          left join fetch o.orderLines
          where o.buyerId = :buyerId
          order by o.createdAt desc
          """,
          countQuery = "select count(o) from Order o where o.buyerId = :buyerId")
    Page<Order> findPageByBuyerIdOrderByCreatedAtDesc(@Param("buyerId") Long buyerId, Pageable pageable);

    /**
     * 전체 주문 목록 페이징 조회 (판매자 전용, 최신 순).
     */
    @Query(value = """
          select distinct o
          from Order o
          left join fetch o.orderLines
          order by o.createdAt desc
          """,
          countQuery = "select count(o) from Order o")
    Page<Order> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

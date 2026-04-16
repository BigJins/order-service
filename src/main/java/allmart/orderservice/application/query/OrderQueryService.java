package allmart.orderservice.application.query;

import allmart.orderservice.adapter.webapi.dto.OrderDetailResponse;
import allmart.orderservice.application.required.OrderDocumentRepository;
import allmart.orderservice.domain.order.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 주문 Query 서비스 — MongoDB 단일 도큐먼트 조회.
 * MySQL JOIN 없음. JPA 트랜잭션 불필요.
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService implements OrderQueryUseCase {

    private final OrderDocumentRepository documentRepository;

    @Override
    public OrderDetailResponse findById(Long orderId) {
        return documentRepository.findById(orderId)
                .map(OrderDetailResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    public Page<OrderDetailResponse> findByBuyer(Long buyerId, Pageable pageable) {
        return documentRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId, pageable)
                .map(OrderDetailResponse::from);
    }

    @Override
    public Page<OrderDetailResponse> findAll(Pageable pageable) {
        return documentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(OrderDetailResponse::from);
    }
}

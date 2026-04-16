package allmart.orderservice.application.required;

import allmart.orderservice.domain.order.document.OrderDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * MongoDB Order 읽기 모델 Repository.
 * CQRS Query 전용 — insert/update는 OrderDocumentSyncHandler가 담당.
 */
public interface OrderDocumentRepository extends MongoRepository<OrderDocument, Long> {

    Page<OrderDocument> findByBuyerIdOrderByCreatedAtDesc(Long buyerId, Pageable pageable);

    Page<OrderDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

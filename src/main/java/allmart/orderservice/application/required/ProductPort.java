package allmart.orderservice.application.required;

import allmart.orderservice.domain.order.Money;

/**
 * 상품 서비스 포트 — application 레이어가 infrastructure에 요구하는 계약.
 * 구현체: adapter/client/ProductServiceClient
 */
public interface ProductPort {

    record ProductInfo(Money price, String taxType) {}

    /** 주문 생성 시 상품 현재 가격 및 세금 유형 조회. 실패 시 예외 전파 → 주문 생성 중단 */
    ProductInfo getProductInfo(Long productId);
}

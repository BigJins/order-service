# order-service

**allmart** 이커머스 플랫폼의 주문 도메인 마이크로서비스입니다.

## 관련 서비스

| 서비스 | 역할 | GitHub |
|--------|------|--------|
| **order-service** | 주문 생성 / 상태 관리 | 현재 레포 |
| **pay-service** | 결제 승인 / Toss Payments 연동 | [BigJins/pay-service](https://github.com/BigJins/pay-service) |
| **apigateway-service** | Rate Limiting / 라우팅 | [BigJins/apigateway-service](https://github.com/BigJins/apigateway-service) |
| **config-server** | 런타임 설정 중앙 관리 | [BigJins/config-server](https://github.com/BigJins/config-server) |
| **allmart-configs** | Config Server 설정 파일 저장소 | [BigJins/allmart-configs](https://github.com/BigJins/allmart-configs) |

## 기술 스택

- **Java 21** + Spring Boot 4.0.2
- **Hexagonal Architecture** (adapter / application / domain)
- **Spring Data JPA** + MySQL 8 (운영) / H2 (테스트)
- **Apache Kafka** — 결제 결과 이벤트 수신 (`payment.result.v1`)
- **Java 21 Virtual Threads** — I/O 대기 중 플랫폼 스레드 점유 최소화
- **Spring Cloud Config Client** — Config Server에서 런타임 설정 주입
- **Micrometer + Prometheus** — 메트릭 수집

## 주요 구현 포인트

### Hexagonal Architecture + DDD
```
adapter/   ← REST API, Kafka Consumer
application/ ← 유스케이스 인터페이스(Port) + 서비스 구현
domain/    ← 핵심 비즈니스 로직, Value Object, Aggregate Root
```
- `Order` Aggregate Root가 `OrderLine` 목록 관리
- `Money`, `Address`, `ShippingInfo` 를 Java Record로 불변 보장
- 상태 전이(`markAsPaid`, `markPaymentFailed`)는 `Order` 엔티티 내부에서만 수행

### 결제 금액 도메인 검증
Kafka 메시지의 결제 금액이 주문 금액과 다를 경우 `IllegalArgumentException` 발생.
검증 로직이 서비스가 아닌 **도메인 레이어**에 위치.

```java
public void markAsPaid(long confirmedAmount) {
    if (this.status == OrderStatus.PAID) return; // 멱등: 중복 메시지 무시
    status.validatePayable();
    if (this.totalAmount.amount() != confirmedAmount) {
        throw new IllegalArgumentException("결제 금액 불일치: ...");
    }
    this.status = OrderStatus.PAID;
}
```

### Kafka 방어 처리
존재하지 않는 `tossOrderId` 수신 시 예외 전파 대신 WARN 로그 후 skip → Kafka 재시도 루프 방지.

### Java 21 Virtual Threads
`spring.threads.virtual.enabled: true` — 100 VU 부하에서 플랫폼 스레드 121개 → 46개 감소 (실측).

## API

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/orders` | 주문 생성 |
| GET | `/api/orders/{orderId}` | 주문 상세 조회 |

## 실행

```bash
# 로컬 MySQL 시작
docker compose up -d

# 서비스 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

> 로컬 실행 전 `application-local.yml` 파일 생성 필요 (`.gitignore`에 포함된 파일)

## 상태 머신

```
PENDING_PAYMENT → PAID
PENDING_PAYMENT → PAYMENT_FAILED → PENDING_PAYMENT (재시도)
PAID / CANCELED → 터미널 (추가 전이 불가)
```

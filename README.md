# order-service

**allmart** 이커머스 플랫폼의 주문 도메인 마이크로서비스입니다.

## 관련 서비스

| 서비스 | 역할 | GitHub |
|--------|------|--------|
| **order-service** | 주문 생성 / 상태 관리 | 현재 레포 |
| **pay-service** | 결제 승인 / Toss Payments 연동 | [BigJins/pay-service](https://github.com/BigJins/pay-service) |
| **delivery-service** | 배송 생성 / 상태 관리 | [BigJins/delivery-service](https://github.com/BigJins/delivery-service) |
| **apigateway-service** | Rate Limiting / 라우팅 | [BigJins/apigateway-service](https://github.com/BigJins/apigateway-service) |

## 기술 스택

- **Java 21** + Spring Boot 4.0.2
- **Hexagonal Architecture** (adapter / application / domain)
- **Spring Data JPA** + MySQL 8 (운영) / H2 (테스트)
- **Apache Kafka** — 결제 결과 수신 (`payment.result.v1`), 배송 완료 수신 (`delivery.completed.v1`)
- **Debezium CDC** — `outbox_event` 테이블 → Kafka `order.paid.v1` 자동 발행
- **Java 21 Virtual Threads** — I/O 대기 중 플랫폼 스레드 점유 최소화
- **Spring Cloud Config + Bus** — 무중단 설정 변경
- **Micrometer + Prometheus** — 메트릭 수집

## 주요 구현 포인트

### 주문 상태 머신

```
PENDING_PAYMENT
    ├──▶ PAID           (payment.result.v1 status=DONE)
    └──▶ PAYMENT_FAILED (payment.result.v1 status=FAILED)

PAYMENT_FAILED ──▶ PENDING_PAYMENT (재결제 허용)
PAID ──▶ CONFIRMED (delivery.completed.v1 수신)
CANCELED / CONFIRMED : 터미널
```

### Outbox 패턴 (Debezium CDC)

```
Order PAID 전이 시:
  DB 트랜잭션 내 outbox_event 저장 (order.paid.v1)
  └─▶ Debezium order-outbox-connector 감지
      └─▶ Kafka order.paid.v1 발행
          └─▶ delivery-service 수신 → 배송 자동 생성
```

> `transforms.outbox.route.topic.replacement: "${routedByValue}"` 설정 필수.
> 없으면 `outbox.event.order.paid.v1` 잘못된 토픽으로 발행됨.

### 결제 금액 도메인 검증

```java
public void markAsPaid(long confirmedAmount) {
    if (this.status == OrderStatus.PAID) return; // 멱등: 중복 메시지 무시
    status.validatePayable();
    if (this.totalAmount.amount() != confirmedAmount) {
        throw new IllegalArgumentException("결제 금액 불일치");
    }
    this.status = OrderStatus.PAID;
}
```

### Kafka 방어 처리

존재하지 않는 `tossOrderId` 수신 시 WARN 로그 후 skip → 재시도 루프 방지

### Java 21 Virtual Threads

`spring.threads.virtual.enabled: true` — 100 VU 부하에서 플랫폼 스레드 121개 → 46개 감소 (실측)

---

## 로컬 vs 운영 환경 차이

| 항목 | 로컬 (local) | 운영 (prod) |
|------|-------------|------------|
| **설정 소스** | config-server optional (없어도 기동) | config-server **필수** (없으면 기동 실패) |
| **DB** | `localhost:3307/order` (Docker) | `${DB_HOST}/${DB_NAME}` (AWS RDS) |
| **DB SSL** | 비활성 | `useSSL=true&requireSSL=true` |
| **JPA DDL** | `update` (자동 스키마 생성) | `validate` (**스키마 불일치 시 기동 실패**) |
| **Kafka Bootstrap** | `localhost:19092,19093,19094` | `${KAFKA_BOOTSTRAP_SERVERS}` |
| **Kafka Consumer** | `auto-startup: false` | `auto-startup: true` |
| **TossPaymentsStub** | `@Profile("local")` 활성 (가짜 결제) | 비활성 → 실제 Toss API |
| **PaymentResultConsumer** | `@Profile("local")` 활성 | 비활성 (Debezium이 Kafka로 직접 발행) |
| **SQL 로그** | `show-sql: true` | `false` |

---

## 배포 절차

### 사전 조건

- AWS RDS MySQL 8 (DB + 계정 준비)
- Kafka 클러스터 (MSK 또는 자체)
- config-server 배포 완료 + allmart-configs 연동
- Debezium Kafka Connect 클러스터 실행 중

### 1단계 — 빌드

```bash
./gradlew build -x test      # 빠른 빌드
./gradlew build              # 테스트 포함 (권장)
```

### 2단계 — Docker 이미지 빌드 & ECR 푸시

```bash
aws ecr get-login-password --region ap-northeast-2 \
  | docker login --username AWS --password-stdin ${ECR_REGISTRY}

docker build -t order-service .
docker tag order-service:latest ${ECR_REGISTRY}/order-service:latest
docker push ${ECR_REGISTRY}/order-service:latest
```

### 3단계 — DB 스키마 마이그레이션 (최초 배포 또는 스키마 변경 시)

```sql
-- outbox_event.payload 타입 확인 (MEDIUMTEXT 이어야 함)
SHOW COLUMNS FROM outbox_event;

-- TEXT인 경우 실행
ALTER TABLE outbox_event MODIFY payload MEDIUMTEXT NOT NULL;
```

### 4단계 — ECS 서비스 배포

```bash
aws ecs update-service \
  --cluster allmart-cluster \
  --service order-service \
  --force-new-deployment \
  --region ap-northeast-2
```

### 5단계 — Debezium 커넥터 등록 (최초 1회)

```bash
curl -X POST http://<kafka-connect>:8083/connectors \
  -H "Content-Type: application/json" \
  -d @debezium/order-outbox-connector.json
```

---

## 배포 시 주의사항

### ⚠️ `ddl-auto: validate` — 스키마 먼저, 배포 나중

운영에서 Hibernate는 스키마를 변경하지 않음. 엔티티 변경 시 SQL 마이그레이션 먼저 실행 후 배포.

### ⚠️ Debezium `route.topic.replacement` 필수

세 커넥터 모두 반드시 포함해야 함:

```json
"transforms.outbox.route.topic.replacement": "${routedByValue}"
```

없으면 `outbox.event.{topic}` 잘못된 토픽으로 발행 → 소비자가 수신 불가.

### ⚠️ `characterEncoding=UTF-8` JDBC URL 필수

MySQL 서버 기본 `character_set_client=latin1`인 경우 없으면 한글 이중 인코딩 저장됨.
`order-service-prod.yml`, `delivery-service-prod.yml` 모두 포함됨.

### ⚠️ config-server 먼저 배포

order-service는 기동 시 config-server에서 설정을 가져옴. config-server가 없으면 기동 실패.
기동 순서: `config-server → order-service, delivery-service, ...`

### ⚠️ 필수 환경 변수

| 변수 | 설명 |
|------|------|
| `CONFIG_SERVER_URI` | config-server 주소 |
| `DB_HOST` | RDS 엔드포인트 |
| `DB_PORT` | DB 포트 (기본 3306) |
| `DB_NAME` | 데이터베이스 이름 |
| `DB_USER` | DB 계정 |
| `DB_PASSWORD` | DB 비밀번호 |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka 브로커 주소 |

### ⚠️ `@Profile("local")` Bean — 운영에서 자동 비활성

| 클래스 | 운영 대체 |
|--------|-----------|
| `TossPaymentsStub` | 실제 Toss Payments API |
| `PaymentResultConsumer` | Debezium CDC → Kafka → 같은 토픽 소비 |

---

## API

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/orders` | 주문 생성 → `tossOrderId`, `amount` 반환 |
| GET | `/api/orders/{orderId}` | 주문 상세 조회 (orderLines 포함) |

## 로컬 실행

```bash
# 1. 인프라 시작 (MySQL, Kafka, Redis, Debezium)
cd /c/newpractice && docker compose up -d

# 2. config-server 먼저 기동
cd /c/newpractice/config-server && ./gradlew bootRun

# 3. order-service 실행
./gradlew bootRun --args='--spring.profiles.active=local'
```

## 테스트

```bash
./gradlew test
# 결과: build/reports/tests/test/index.html
```

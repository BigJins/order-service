# chat-service 구현 계획

> **작성일**: 2026-04-09
> **목적**: 대화형 음성 주문 AI (allmart 마트 직원 챗봇) 구현 순서 및 선행 조건 정리

---

## 현재 상태 진단

**존재하지 않는 서비스:**
- `chat-service` (포트 8094) — 신규 생성 필요
- `search-service` (포트 8090, ES) — 신규 생성 필요

**기존 서비스 API 갭:**

| 필요한 것 | 현재 상태 | 대상 서비스 |
|-----------|-----------|------------|
| 상품 단위 정보 (`unit`, `unitSize`) | `ProductPriceResponse`에 없음 | product-service |
| 최근 주문 이력 조회 (`buyerId` 기준) | 엔드포인트 없음 | order-query-service |
| `product.deleted.v1` 이벤트 | 미설계 | product-service |

---

## 전체 구현 순서

```
Phase 0  기존 서비스 소규모 수정 (선행 조건)
Phase 1  chat-service 뼈대 (지금 당장 시작 가능)
Phase 2  Function Calling 추가
Phase 3  ES + search-service
Phase 4  RAG 상품 추천
Phase 5  음성 입력 (프론트엔드)
Phase 6  평가
```

---

## Phase 0 — 기존 서비스 소규모 수정

> chat-service의 Function Calling이 의존하는 API 선행 작업.
> order-service 코드 변경 없음.

### A. product-service — 상품 단위 정보 추가

```
변경 대상: GET /internal/products/{id}/price 응답

현재 ProductPriceResponse:
  productId, productName, price, taxType

추가 필요:
  unit      (String)  예: "박스", "팩", "개"
  unitSize  (Integer) 예: 10 (1박스 = 10개)

필요 이유:
  "감귤 2개요" → "감귤은 박스 단위(10개)예요. 몇 박스 드릴까요?"
  단위 모호성 해소 → Function Calling getProductUnit() 에서 사용
```

### B. order-query-service — 최근 주문 조회 엔드포인트 추가

```
추가 API: GET /api/orders/recent?buyerId={id}
응답: 최근 주문 1건 (상품명, 수량, 배송지)

필요 이유:
  "저번이랑 똑같이요" → 이전 주문 이력 조회
  "지난번 배송지로요" → 이전 배송지 자동 불러오기
  Function Calling getRecentOrder(), getRecentDeliveryAddress() 에서 사용
```

### C. CLAUDE.md 이벤트 계약 추가

```
product.deleted.v1 이벤트 설계 추가
  발행: product-service (상품 삭제 시)
  소비: chat-service → ES에서 productId로 즉시 삭제

ES 구축 전이라도 미리 설계해두어야 나중에 누락 없음
```

---

## Phase 1 — chat-service 뼈대 ✅ 완료 (2026-04-09)

> Phase 0 없이도 바로 시작 가능.
> 텍스트 기반 주문 대화가 먼저 동작하는 것을 확인 후 기능을 붙이는 방식.

### 생성한 것

```
신규 서비스: chat-service (포트 8094)
위치: /c/newpractice/chat-service/
언어/프레임워크: Java 21, Spring Boot 3.4.5, Spring WebFlux
```

### 구현 결과

#### 1. 의존성 구성

- **Spring AI 1.0.0** — `spring-ai-starter-model-anthropic` (Claude Sonnet 4.6 연동)
- `spring-boot-starter-webflux` — SSE 스트리밍
- `ANTHROPIC_API_KEY` 환경변수 → `spring.ai.anthropic.api-key`로 주입
- 내부 서비스 호출: `RestClient` (동기, Virtual Threads 기반)
- `spring.threads.virtual.enabled=true` — 블로킹 RestClient 호출 안전

#### 2. Claude Sonnet 4.6 연동

- Spring AI `ChatClient.Builder` 자동 주입 (`AiConfig.java`에서 `ChatClient` Bean 생성)
- `chatClient.prompt().system(...).messages(...).toolCallbacks(...).stream().content()` — `Flux<String>` 반환
- Spring AI가 Tool Calling 루프(도구 호출 → 결과 주입 → 재응답)를 내부에서 자동 처리
- `application.yml`: `spring.ai.anthropic.chat.options.model: claude-sonnet-4-6`

#### 3. 다중 턴 대화 이력 관리

- 10턴 슬라이딩 윈도우 (user + assistant 각 10개, 최대 20개 메시지)
- `ConcurrentHashMap<Long, ChatSession>` 인메모리 저장 (buyerId 키)
- `DELETE /api/chat/sessions/{buyerId}` — 세션 초기화 API
- Phase 2 이후 Redis TTL 세션으로 교체 예정

#### 4. 시스템 프롬프트 (SystemPromptBuilder)

- 페르소나: "allmart 강남점 직원, 1~3문장, 친근한 말투"
- 정적 상품 목록 7종 (productId, 단가, 단위, 단위수량 포함) — Phase 4 RAG 전까지 사용
- 주문 처리 순서 명시 (상품→결제→배송지→확인→##ORDER_CONFIRM##)
- Few-Shot 예시 1세트 포함

#### 5. ##ORDER_CONFIRM## 흐름

```
Claude 출력: "주문해 드릴게요!\n##ORDER_CONFIRM##{"martId":1,...}"
    ↓
OrderConfirmParser: MARKER 위치 검출 → JSON 파싱 → OrderConfirmData
    ↓
OrderServiceClient (RestClient): OrderConfirmData + buyerId → POST /api/orders
    → Mono.fromCallable().subscribeOn(Schedulers.boundedElastic()) 로 reactive chain 연결
    ↓
SSE event: order_created {"orderId":1,"tossOrderId":"...","amount":30000}
```

#### 6. SSE 스트리밍 API

```
POST /api/chat/stream
Content-Type: application/json
Body: {"buyerId": 42, "message": "감귤 2개 주세요"}

Response: text/event-stream
  event: text\ndata: 감귤은...
  event: text\ndata: 1박스(10개)...
  event: order_created\ndata: {"orderId":1,"tossOrderId":"ORD_...","amount":30000}
```

- EventSource는 POST 미지원 → Vue 3에서 `fetch()` + `ReadableStream`으로 소비
- `X-User-Id` 헤더(Gateway 주입) 우선, 없으면 body buyerId 사용

### 아키텍처 (헥사고날)

```
adapter/webapi/    ChatApi.java              POST /api/chat/stream, DELETE /api/chat/sessions/{id}
                   GlobalExceptionHandler    400/500 표준 에러 응답
adapter/client/    ProductServiceClient      product-service GET /internal/products/{id}/price (RestClient)
                   OrderQueryServiceClient   order-query-service GET /api/orders/recent (RestClient)
                   OrderServiceClient        order-service POST /api/orders (RestClient + boundedElastic)
                   SearchServiceClient       search-service GET /internal/search/products/rag (RestClient)
config/            AiConfig                  Spring AI ChatClient Bean
                   WebClientConfig           RestClient 4개 Bean + ObjectMapper
application/       ChatService               Spring AI ChatClient 스트리밍 + FunctionCallback 도구 등록
                   OrderConfirmParser        ##ORDER_CONFIRM## 파싱
domain/session/    ChatSession               슬라이딩 윈도우 세션
                   ChatSessionStore          buyerId → session ConcurrentHashMap
domain/prompt/     SystemPromptBuilder       정적 상품 목록 + 시스템 프롬프트
domain/tool/       ChatToolExecutor          getProductUnit / getRecentOrder / searchProducts 동기 실행
```

### 실행 방법

```bash
export ANTHROPIC_API_KEY=sk-ant-...
cd /c/newpractice/chat-service
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Phase 1 완료 기준 달성

```
✅ "감귤 2개 주세요" 텍스트 입력 → Claude 대화 응답
✅ 단위 모호성 시 되묻기 (시스템 프롬프트 내 상품 단위 정보 포함)
✅ 결제 수단, 배송지 수집 대화
✅ "제주 감귤 2박스 30,000원 맞으시죠?" 최종 확인
✅ POST /api/orders 호출 → tossOrderId 반환
✅ SSE 스트리밍으로 실시간 응답
```

### 설계 결정 기록

| 결정 | 선택 | 이유 |
|------|------|------|
| Spring AI 버전 | 1.0.0 GA (spring-ai-starter-model-anthropic) | Spring Boot 3.4.5 기반. Boot 4.x는 Spring Framework 7.x → Spring AI BOM 충돌 |
| Spring Boot 버전 | 3.4.5 (다른 서비스는 4.0.2) | Spring AI 1.x가 Spring Framework 6.x 타겟. chat/search 두 서비스만 3.x |
| WebMVC vs WebFlux | WebFlux | SSE Flux 스트리밍이 핵심. JPA 없으므로 블로킹 I/O 없음 |
| 내부 HTTP 클라이언트 | RestClient (동기) | Tool Callback은 동기 함수. Spring AI가 boundedElastic에서 실행 |
| Virtual Threads | `spring.threads.virtual.enabled=true` | RestClient 동기 블로킹 호출을 reactive pipeline 내부에서 안전하게 실행 |
| EventSource vs fetch | fetch() + ReadableStream | EventSource는 POST 미지원 |
| 세션 저장 | 인메모리 ConcurrentHashMap | Phase 1 단순화. Phase 2에서 Redis로 교체 |
| LLM 토큰 관리 위치 | chat-service 내부 | Gateway는 stateless 유지. 세션 상태는 chat-service 책임 |

---

## Phase 2 — Function Calling ✅ 완료 (2026-04-09)

> Phase 0 선행 조건 확인 완료 후 구현.
> - product-service `InternalProductPriceResponse`에 `unit`, `unitSize` 이미 포함 ✅
> - order-query-service `GET /api/orders/recent?buyerId={id}` 이미 존재 ✅

### 구현한 Tool 2개

계획의 Tool 3개(getProductUnit, getRecentOrder, getRecentDeliveryAddress)를
**2개로 통합** — `getRecentDeliveryAddress`는 `getRecentOrder`와 동일 API라 분리 불필요.

| 도구 | 설명 | 내부 호출 |
|------|------|-----------|
| `getProductUnit(productId)` | 상품 단위/수량/단가 조회 | product-service `GET /internal/products/{id}/price` |
| `getRecentOrder()` | 최근 주문(상품+배송지) 조회 | order-query-service `GET /api/orders/recent?buyerId=` |

- `getRecentOrder`의 `buyerId`는 Claude 입력이 아닌 서버에서 직접 주입 (보안)

### Spring AI Tool Calling 흐름

```
ChatService.stream(buyerId, message)
  → chatClient.prompt()
      .system(systemPrompt)
      .messages(history)                    // Spring AI Message 타입으로 변환
      .toolCallbacks(buildToolCallbacks())  // FunctionCallback 2개 등록
      .stream()
      .content()                            // Flux<String> 반환
          ↓
  Spring AI 내부 자동 처리:
    도구 호출 필요 → FunctionCallback 동기 실행 (boundedElastic)
    → 결과 주입 후 재응답 → 최종 텍스트 청크만 Flux에 emit
          ↓
  SSE text 이벤트로 청크 emit
  스트림 완료 후 ##ORDER_CONFIRM## 파싱 → order-service 호출
```

> Phase 2 수동 구현 대비 변경: 1차 non-streaming + 2차 streaming 분리 구조 제거.
> Spring AI가 내부적으로 tool calling 루프를 관리 → ChatService 코드 대폭 단순화.

### Tool Callback 정의 방식

```java
// ChatService.buildToolCallbacks(Long buyerId) — buyerId 클로저로 캡처
FunctionCallback.builder()
    .function("getProductUnit",
            (GetProductUnitRequest req) -> toolExecutor.getProductUnit(req.productId()))
    .description("상품의 단위와 단가를 조회합니다.")
    .inputType(GetProductUnitRequest.class)
    .build()

// Tool 입력 타입 — Spring AI가 Claude JSON → Record 자동 역직렬화
record GetProductUnitRequest(@JsonProperty("productId") Long productId) {}
record GetRecentOrderRequest() {}   // 파라미터 없음 — buyerId는 클로저에서
```

### 신규/수정 파일

**신규:**
```
config/AiConfig.java                       Spring AI ChatClient Bean
adapter/client/ProductServiceClient.java   RestClient 동기 호출
adapter/client/OrderQueryServiceClient.java RestClient 동기 호출
adapter/client/dto/ProductUnitInfo.java    product 응답 DTO
adapter/client/dto/RecentOrderInfo.java    recent order 응답 DTO (중첩 record)
```

**수정:**
```
build.gradle.kts          Spring Boot 3.4.5, spring-ai-starter-model-anthropic, Spring AI BOM 1.0.0
settings.gradle.kts       milestone repo 추가
application.yml           anthropic.model → spring.ai.anthropic.chat.options.model
                          spring.threads.virtual.enabled=true 추가
application-local.yml     anthropic.api-key → spring.ai.anthropic.api-key
WebClientConfig.java      WebClient 3개 → RestClient 3개 (Anthropic WebClient 제거)
ChatToolExecutor.java     Mono<String> → String (동기 전환, ReactorFlatMap 제거)
ChatService.java          Spring AI ChatClient 기반 완전 재작성
                          buildToolCallbacks(buyerId), buildSpringAiMessages() 추가
OrderServiceClient.java   WebClient → RestClient (동기)
```

**삭제:**
```
adapter/client/AnthropicClient.java
adapter/client/dto/AnthropicMessage.java
adapter/client/dto/AnthropicRequest.java
adapter/client/dto/AnthropicNonStreamResponse.java
adapter/client/dto/ToolDefinition.java
```

### Phase 2 완료 기준 달성

```
✅ "저번이랑 똑같이요" → getRecentOrder 도구 호출 → 최근 주문 자동 불러오기
✅ "감귤 2개요" → getProductUnit 도구 호출 → "박스 단위예요. 몇 박스?" 자동 되묻기
✅ "지난번 배송지로요" → getRecentOrder 도구 호출 → 이전 배송지 자동 적용
```

### 설계 결정 기록

| 결정 | 선택 | 이유 |
|------|------|------|
| Tool 3개 → 2개 통합 | getRecentOrder가 배송지 포함 | 동일 API 중복 호출 불필요. Claude가 결과에서 배송지 추출 |
| buyerId Tool 입력 여부 | 서버 주입 (클로저) | Claude에게 buyerId 노출 불필요. 서버가 세션에서 알고 있음 |
| tool_calling SSE 이벤트 | 제거 | Spring AI 내부 처리로 도구 실행 시점 불투명. 단순성 우선 |
| Tool Callback 실행 방식 | 동기 (Spring AI boundedElastic 실행) | FunctionCallback은 동기 함수. Virtual Threads로 블로킹 안전 |
| 수동 Tool Use 루프 | 제거 | Spring AI가 tool calling 루프 자동 관리 (코드 100줄 이상 감소) |

---

## Phase 3 — ES + search-service ✅ 완료 (2026-04-09)

> Phase 2 완료 후. RAG의 선행 인프라.

### 구현 결과

#### 1. docker-compose.yml 변경

- `elasticsearch:8.13.0` 추가 (포트 9200, xpack.security=false, nori 플러그인 자동 설치)
- `kafka-ui` 포트 8090 → 8099 (search-service 포트 확보)
- `mysql-product`에 binlog 설정 추가 (Debezium CDC 대상)
- `elasticsearch` 컨테이너 `es-data` 볼륨 추가

#### 2. product-service 변경

- `OutboxEvent` 도메인 엔티티 추가 (`domain/event/OutboxEvent.java`)
- `OutboxRepository` 인터페이스 추가 (`application/required/`)
- `ProductService.register()` → `product.registered.v1` Outbox 저장
- `ProductService.delete()` → `product.deleted.v1` Outbox 저장
- `GET /internal/products/all` 배치 색인 API 추가 (페이징, DELETED 제외)
- `ProductIndexResponse` DTO 추가
- `jackson-databind` 의존성 명시 추가

#### 3. Debezium 커넥터

- `product-outbox-connector.json` 추가 (productdb.outbox_event 감시)
- `debezium-init`에 product-outbox-connector 등록 추가
- `kafka-connect` depends_on에 mysql-product 추가

#### 4. search-service 신규 생성 (포트 8090)

```
위치: /c/newpractice/search-service/
언어/프레임워크: Java 21, Spring Boot 3.4.5, Spring WebMVC
```

**핵심 컴포넌트:**

| 클래스 | 역할 |
|--------|------|
| `ProductDocument` | ES 도큐먼트 (`@Document`, nori analyzer, dense_vector 1536) |
| `ProductIndexService` | ES upsert/delete + Spring AI `EmbeddingModel` 임베딩 생성 |
| `ProductSearchService` | 키워드 + 필터 검색 (Phase 4에서 kNN 하이브리드로 교체 예정) |
| `ProductRegisteredConsumer` | `product.registered.v1` → ES upsert |
| `ProductDeletedConsumer` | `product.deleted.v1` → ES 삭제 |
| `ProductServiceClient` | `GET /internal/products/all` 배치 조회 (RestClient) |
| `ProductIndexInitializer` | ApplicationRunner — 시작 시 전체 상품 배치 색인 (100개씩, 100ms 딜레이) |
| `SearchApi` | `GET /internal/search/products?q=&categoryId=&maxPrice=&inStock=` |

**ES 인덱스 설계:**

```
인덱스: products
설정:   nori_analyzer (nori_tokenizer + lowercase)
필드:
  productId     keyword
  productName   text(nori_analyzer) + keyword sub-field
  categoryId    long
  categoryName  keyword
  sellingPrice  long
  inStock       boolean
  unit          keyword
  unitSize      integer
  status        keyword
  embeddingText text
  embedding     dense_vector(1536, index:true, similarity:cosine)
  updatedAt     date
```

**임베딩 텍스트 포맷:**

```
"상품명: {name} 카테고리: {categoryName} 설명: {desc} 단위: {unit} 가격: {price}원"
```

**임베딩 실패 처리:**

- Spring AI `EmbeddingModel.embed()` 실패 시 WARN 로그 + 빈 벡터(float[1536]) → 서비스 중단 없음
- 배치 색인 중 product-service 실패 → WARN 후 skip (서비스 기동 차단 안 함)

**의존성:**

```kotlin
// build.gradle.kts
extra["springAiVersion"] = "1.0.0"
implementation("org.springframework.ai:spring-ai-starter-model-openai")
mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
```

```yaml
# application.yml
spring:
  ai:
    openai:
      embedding:
        options:
          model: text-embedding-3-small

# application-local.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
```

### 설계 결정 기록

| 결정 | 선택 | 이유 |
|------|------|------|
| Spring AI 사용 | `EmbeddingModel` (spring-ai-starter-model-openai) | chat-service와 동일하게 3.4.5 기반. RestClient 직접 구현 제거 |
| Spring Boot 버전 | 3.4.5 | Spring AI 1.x BOM이 Spring Framework 6.x 타겟. Boot 4.x와 호환 불가 |
| WebFlux vs WebMVC | WebMVC | search-service는 SSE 불필요. 동기 REST + ES 조합이 더 단순 |
| nori 플러그인 설치 | docker entrypoint에서 자동 설치 | 커스텀 이미지 빌드 없이 로컬 개발 편의성 유지 |
| 초기 색인 조건 | 인덱스 비어있을 때만 | 재시작 시 중복 색인 방지. 수동 재색인이 필요하면 인덱스 삭제 후 재시작 |
| 인덱스 자동 생성 | ApplicationRunner에서 명시적 생성 | `@Document(createIndex=false)` — settings+mappings 적용 보장 |

### Phase 3 완료 기준 달성

```
✅ product-service 상품 등록 → product.registered.v1 Outbox → Debezium → Kafka → ES upsert
✅ product-service 상품 삭제 → product.deleted.v1 Outbox → Debezium → Kafka → ES 삭제
✅ search-service 시작 시 기존 상품 전체 배치 색인
✅ GET /internal/search/products?q=감귤 → ES 검색 결과 반환
✅ 임베딩 생성 실패 시 서비스 중단 없이 빈 벡터로 색인 유지
```

---

## Phase 4 — RAG 상품 추천 ✅ 완료 (2026-04-09)

> Phase 3 완료 후. chat-service에 searchProducts 도구 추가.

### 구현 결과

#### 아키텍처 결정: Tool-based RAG

QuestionAnswerAdvisor(매 메시지마다 자동 검색) 대신 **Tool Calling RAG** 선택.
Claude가 상품 탐색이 필요할 때만 `searchProducts` 도구를 직접 호출.

| 방식 | 특징 | 선택 이유 |
|------|------|-----------|
| QuestionAnswerAdvisor | 매 메시지마다 자동 검색 후 프롬프트 주입 | "배송지는 강남구로요" 같은 무관한 메시지에도 검색 발생 — 낭비 |
| Tool-based RAG ✅ | Claude가 필요 판단 시 도구 호출 | 검색 비용 최소화. 기존 FunctionCallback 구조와 일관됨 |

#### search-service 변경

**`ProductSearchService.java`** — `ragSearch()` 메서드 추가:
```
1. EmbeddingModel.embed(query) → float[] 쿼리 벡터 생성
2. NativeQuery + KnnQuery (Spring Data ES 5.x):
   field: "embedding", numCandidates: size×10, k: size
   filter: inStock=true 항상 적용
3. 임베딩 실패 시 키워드 검색으로 자동 폴백 (서비스 중단 없음)
```

**`SearchApi.java`** — `GET /internal/search/products/rag` 엔드포인트 추가:
```
파라미터: q (자연어 쿼리), size (기본 5, 최대 10)
반환: List<ProductSearchResponse> (productId, 단가, 단위 포함)
```

#### chat-service 변경

**신규 파일:**
```
adapter/client/SearchServiceClient.java       search-service /rag 호출 (RestClient)
adapter/client/dto/ProductSearchResult.java   검색 결과 DTO
```

**수정 파일:**
```
application.yml        search-service.url 추가
application-local.yml  http://localhost:8090 추가
WebClientConfig.java   searchServiceRestClient Bean 추가
ChatToolExecutor.java  searchProducts(query) → formatSearchResults() 추가
ChatService.java       searchProducts FunctionCallback + SearchProductsRequest record 추가
SystemPromptBuilder.java 정적 상품 목록 제거 → searchProducts 도구 사용 안내로 교체
```

**searchProducts 도구 결과 포맷** (Claude tool_result로 전달):
```
검색 결과 (3개):
1. 딸기 (productId:4) - 8,000원/팩(1개) - 재고있음
2. 한라봉 (productId:6) - 18,000원/박스(8개) - 재고있음
3. 천혜향 (productId:7) - 22,000원/박스(10개) - 재고있음
```

→ Claude가 이 결과를 바탕으로 추천 + ##ORDER_CONFIRM## JSON에 productId/unitPrice 직접 사용.

#### 전체 흐름

```
고객: "달달한 간식 추천해줘요"
    ↓
ChatService.stream()
  → chatClient.stream() with searchProducts FunctionCallback
    ↓
Spring AI 내부: Claude가 searchProducts("달달한 간식") 호출 판단
  → ChatToolExecutor.searchProducts("달달한 간식")
  → SearchServiceClient.ragSearch("달달한 간식", 5)
  → search-service GET /internal/search/products/rag?q=달달한 간식&size=5
  → ProductSearchService.ragSearch() → EmbeddingModel.embed() → NativeQuery kNN
  → List<ProductSearchResponse> 반환
    ↓
Spring AI: tool_result 주입 후 Claude 재응답
  → "딸기(8,000원/팩), 한라봉(18,000원/박스) 등이 있어요. 어떤 걸로 드릴까요?"
    ↓
SSE text 이벤트로 스트리밍
```

### 설계 결정 기록

| 결정 | 선택 | 이유 |
|------|------|------|
| RAG 방식 | Tool-based (searchProducts) | 매 메시지 자동 검색 불필요. Claude가 맥락 판단 후 선택적 호출 |
| 검색 방식 | kNN 벡터 검색 (폴백: 키워드) | 의미 기반 추천("달달한 간식"). 키워드 매칭 한계 극복 |
| 정적 상품 목록 | 완전 제거 | DB가 단일 진실 출처. 정적 목록은 재고/가격 불일치 발생 가능 |
| 임베딩 실패 처리 | 키워드 폴백 | OpenAI API 일시 장애 시 서비스 중단 없음 |
| inStock 필터 | 항상 적용 | 품절 상품 추천 방지. 고객 경험 보호 |
| searchProducts 결과 개수 | 최대 5개 | 너무 많으면 Claude context 낭비. 추천 UX에 5개 적당 |

### Phase 4 완료 기준 달성 (RAG)

```
✅ "달달한 간식 추천해줘" → searchProducts 도구 호출 → kNN 검색 → top-5 추천
✅ 품절 상품은 추천에서 제외 (inStock 필터 항상 적용)
✅ 추천 후 "딸기로 할게요" → 주문 흐름 연결 (productId/단가 도구 결과에서 바로 사용)
✅ 정적 상품 목록 제거 — 실시간 DB 기반 검색으로 전환
✅ ES 임베딩 실패 시 서비스 중단 없이 키워드 검색 폴백
```

---

## Phase 4b — 다중 상품 장바구니 + 배달료 정책 ✅ 완료 (2026-04-09)

> Phase 4 RAG와 같은 세션에서 추가 구현.
> 코드 모델 변경 없음 (orderLines는 이미 List) — 시스템 프롬프트와 설정만 변경.

### 추가된 기능

| 기능 | 설명 |
|------|------|
| 다중 상품 동시 주문 | "감귤 2박스랑 딸기 1팩 주세요" → orderLines 배열에 모두 포함 |
| 배달료 안내 | 합계 < 무료 기준 시 "배달료 X원 추가돼요" 자동 안내 |
| 무료 배달 유도 | "Y원 더 추가하시면 배달료 무료예요!" 힌트 제공 |
| 최종 금액 명세 | "상품 합계 X원 + 배달료 Y원 = 총 Z원" 형식으로 확인 |

### 배달료 정책 — 서버 vs 챗봇 역할 분리

```
order-service (서버):
  Order.create() → MartDeliveryConfig → deliveryFee, totalAmount, chargeLines 자동 계산
  → ##ORDER_CONFIRM## JSON에 배달료 미포함 (서버가 계산하므로)

chat-service (챗봇):
  SystemPromptBuilder → @Value 주입으로 동적 프롬프트 생성
  → Claude가 고객에게 배달료 안내 + 무료 유도만 담당
  → ORDER_CONFIRM JSON에는 chargeLines 포함 안 함
```

### SystemPromptBuilder 변경

**Phase 4 이전**: 정적 문자열 프롬프트
**Phase 4b 이후**: `@Value` 주입 + `String.formatted()` 동적 생성

```java
@Component
public class SystemPromptBuilder {
    private final long deliveryFeeAmount;
    private final long freeDeliveryThreshold;

    public SystemPromptBuilder(
            @Value("${mart.delivery-fee-amount:3000}") long deliveryFeeAmount,
            @Value("${mart.free-delivery-threshold:30000}") long freeDeliveryThreshold) { ... }

    public String build() {
        return """
                [배달료 정책 — 반드시 고객에게 안내할 것]
                - 상품 합계 %,d원 이상: 배달료 무료
                - 상품 합계 %,d원 미만: 배달료 %,d원 추가
                - 합계가 %,d원 미만이면 최종 확인 전에 "X원 더 추가하시면 배달료 무료예요!" 안내.
                ...
                """.formatted(freeDeliveryThreshold, freeDeliveryThreshold, deliveryFeeAmount,
                              freeDeliveryThreshold, deliveryFeeAmount,
                              freeDeliveryThreshold, deliveryFeeAmount);
    }
}
```

### application.yml 추가 설정

```yaml
mart:
  delivery-fee-amount: ${MART_DELIVERY_FEE:3000}
  free-delivery-threshold: ${MART_FREE_DELIVERY_THRESHOLD:30000}
```

> 마트별 배달료 정책이 다를 경우 환경변수만 변경하면 됨. 재배포 불필요 (Spring Cloud Config Bus refresh 가능).

### 시스템 프롬프트 핵심 지침

```
[주문 처리 순서]
1. 상품 + 수량 파악 (다중 상품 동시 주문 가능)
   → "더 담으실 게 있으신가요?" 확인
2. 배달료 안내: 합계 계산 → 무료 기준 충족 여부
   → 미달 시: "현재 합계 X원이에요. Y원 더 추가하시면 배달료 무료예요!"
   → 고객이 더 담지 않겠다면: 배달료 포함 최종 금액 안내
3. 결제 수단 (CARD / CASH_ON_DELIVERY) — CASH_PREPAID는 음성 주문 미지원
   → CARD: "주문 완료 후 결제 창이 열립니다. 카드 결제를 진행해 주세요."
   → CASH_ON_DELIVERY: "배달 시 기사님께 현금으로 결제하시면 돼요."
4. 배송지 (우편번호 + 도로명 + 상세)
5. 최종 확인: "상품 합계 X원 + 배달료 Y원 = 총 Z원"
6. 고객 확인 후 ##ORDER_CONFIRM## 출력
```

### ##ORDER_CONFIRM## JSON 변경점

**변경 없음**: `orderLines` 배열은 이미 복수 상품 지원. `chargeLines` 미포함 (서버 계산).

```json
##ORDER_CONFIRM##{"martId":1,"martName":"강남 allmart",
  "orderLines":[
    {"productId":1,"productNameSnapshot":"제주 감귤","unitPrice":15000,"quantity":2},
    {"productId":4,"productNameSnapshot":"딸기","unitPrice":8000,"quantity":1}
  ],
  "payMethod":"CARD","zipCode":"06234",
  "roadAddress":"서울시 강남구 테헤란로 123","detailAddress":"101호","deliveryRequest":null}
```

### 대화 예시 — 배달료 무료 달성

```
고객: 감귤 2박스랑 딸기 1팩 주세요
직원: 제주 감귤 2박스(30,000원)에 딸기 1팩(8,000원), 합계 38,000원이에요. 배달료 무료입니다! 더 담으실 게 있으신가요?

고객: 아니요 괜찮아요
직원: 결제는 카드로 하시겠어요, 현금으로 하시겠어요?
```

### 대화 예시 — 배달료 발생 + 무료 유도

```
고객: 감귤 1박스 주세요
직원: 제주 감귤 1박스(15,000원)이에요. 현재 합계 15,000원이라 배달료 3,000원이 추가돼요. 15,000원 더 추가하시면 배달료 무료예요! 더 담으실 게 있으신가요?

고객: 아니요 그냥 할게요
직원: 상품 합계 15,000원 + 배달료 3,000원 = 총 18,000원이에요. 결제는 카드로 하시겠어요?
```

### 수정 파일

```
application.yml          mart.delivery-fee-amount / mart.free-delivery-threshold 추가
SystemPromptBuilder.java @Value 생성자 주입 + String.formatted() 동적 프롬프트
```

### 설계 결정 기록

| 결정 | 선택 | 이유 |
|------|------|------|
| 배달료 계산 위치 | 서버(order-service) | MartDeliveryConfig가 이미 존재. chat-service가 계산하면 정책 중복 관리 |
| 무료 유도 로직 | Claude가 머릿속으로 계산 | 시스템 프롬프트로 행동 지침만 부여. 별도 계산 코드 불필요 |
| 마트별 설정 주입 | @Value + application.yml 환경변수 | Spring Cloud Config Bus로 재배포 없이 정책 변경 가능 |
| ORDER_CONFIRM 배달료 | 미포함 | 서버가 자동 계산. 챗봇이 계산 실수 시 주문 금액 불일치 방지 |
| 다중 상품 모델 | 코드 변경 없음 | orderLines는 이미 List<OrderLineRequest>. 프롬프트 지침만 추가 |

### Phase 4b 완료 기준 달성

```
✅ "감귤 2박스랑 딸기 1팩 주세요" → orderLines에 2개 상품 포함한 ORDER_CONFIRM 출력
✅ 합계 < 30,000원 시 배달료 3,000원 안내 + 무료 기준까지 차액 유도
✅ 합계 ≥ 30,000원 시 "배달료 무료입니다!" 안내
✅ 고객이 추가 담기 거부 시 배달료 포함 총액으로 최종 확인 진행
✅ mart.delivery-fee-amount / mart.free-delivery-threshold 환경변수로 정책 변경 가능
```

---

## Phase 4c — 안정성 + 비즈니스 로직 보완 ✅ 완료 (2026-04-10)

> Phase 4b 직후 발견된 이슈 수정 및 비즈니스 로직 갭 해소.

### 1. ORDER_CONFIRM 중복 주문 방지

**문제**: `##ORDER_CONFIRM##`이 포함된 assistant 메시지가 ChatSession 이력에 남음 → 고객이 이후 메시지 전송 시 Claude가 동일한 `##ORDER_CONFIRM##`을 다시 출력 → `POST /api/orders` 두 번 호출 → 중복 주문 생성.

**해결**: `ChatService.handleOrderConfirm()`에서 ORDER_CONFIRM 감지 즉시 `sessionStore.remove(buyerId)` 호출. 주문 성공/실패 무관하게 세션 리셋.

```java
// ChatService.handleOrderConfirm()
Optional<OrderConfirmData> confirmOpt = orderConfirmParser.parse(fullText);
if (confirmOpt.isEmpty()) return Flux.empty();

// ORDER_CONFIRM 감지 즉시 세션 리셋
sessionStore.remove(buyerId);
```

### 2. 세션 TTL (메모리 누수 방지)

**문제**: `ConcurrentHashMap<Long, ChatSession>` — 만료 로직 없음. 장기간 운영 시 비활성 세션이 메모리에 무한 누적.

**해결**: Guava Cache `expireAfterAccess(30분)`으로 교체. 마지막 접근 기준 30분 비활성 시 자동 만료.

```java
// ChatSessionStore.java
private final Cache<Long, ChatSession> store = CacheBuilder.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build();
```

**의존성 추가**: `com.google.guava:guava:33.4.8-jre`

### 3. 결제 수단 — CASH_PREPAID 제거

**이유**: CASH_PREPAID(현금 선불)는 마트 내부에서만 의미 있는 결제 수단. 배달 음성 주문에는 부적합.

**변경**: CARD / CASH_ON_DELIVERY 두 가지만 허용. 시스템 프롬프트 및 ORDER_CONFIRM payMethod 허용값 수정.

### 4. CASH_ON_DELIVERY 자동 처리 확인

**확인**: order-service `Order.create()`에서 이미 완전 구현됨. 별도 수정 불필요.

```java
// Order.create() — 이미 구현된 코드
order.status = (req.payMethod() == OrderPayMethod.CASH_ON_DELIVERY)
        ? OrderStatus.PAID : OrderStatus.PENDING_PAYMENT;
```

```java
// OrderModifyService.create() — 이미 구현된 코드
if (saved.getPayMethod() == OrderPayMethod.CASH_ON_DELIVERY) {
    outboxEventPublisher.publishOrderPaid(saved);  // 배송 즉시 트리거
    confirmInventory(saved.getTossOrderId());
}
```

후불 현금 주문은 생성 즉시 PAID 상태로 전이 + `order.paid.v1` 발행 → delivery-service가 배송 자동 생성.

### 5. 재고 부족 오류 안내

**문제**: `POST /api/orders` 400 응답 시 일반 `error` SSE 이벤트만 반환 → 고객이 원인 모름.

**해결**:
- `OrderCreateException` 신규 생성 (statusCode + message 보유)
- `OrderServiceClient` — `RestClientResponseException` catch → order-service 응답 body의 `message` 필드 파싱 → `OrderCreateException` 변환
- `ChatService` — `OrderCreateException`은 `text` SSE로 원인 직접 전달, 5xx는 기존 `error` 이벤트 유지

```
재고 부족 시 흐름:
POST /api/orders → 400 {"code":"OUT_OF_STOCK","message":"재고가 부족합니다. productId=3"}
  → OrderServiceClient: RestClientResponseException → 메시지 파싱 → OrderCreateException
  → ChatService: onErrorResume(OrderCreateException) → SSE event:text "재고가 부족합니다..."
  → 고객: 재고 부족 원인 직접 확인 가능
```

### 6. CARD 결제창 안내

**문제**: ORDER_CONFIRM 이후 프론트엔드가 Toss 결제창을 여는데, Claude가 고객에게 안내 없음 → 고객이 다음에 무엇을 해야 할지 모름.

**해결**: 시스템 프롬프트에 CARD 선택 시 최종 확인 메시지에 "주문 완료 후 결제 창이 열립니다. 카드 결제를 진행해 주세요." 포함 지침 추가.

### 신규/수정 파일

```
신규:
  adapter/client/OrderCreateException.java   주문 생성 실패 예외 (statusCode + message)

수정:
  build.gradle.kts             guava:33.4.8-jre 추가
  domain/session/ChatSessionStore.java       ConcurrentHashMap → Guava Cache (TTL 30분)
  adapter/client/OrderServiceClient.java     에러 응답 파싱 + OrderCreateException 변환
  application/ChatService.java               ORDER_CONFIRM 감지 시 세션 리셋 + 오류 분기 처리
  domain/prompt/SystemPromptBuilder.java     CASH_PREPAID 제거, CARD 결제창 안내, CASH_ON_DELIVERY 안내
```

### 설계 결정 기록

| 결정 | 선택 | 이유 |
|------|------|------|
| ORDER_CONFIRM 후 세션 리셋 시점 | 감지 즉시 (주문 성공 전) | 실패해도 이력에 마커가 남으면 재주문 위험. 실패 시 고객이 새로 시도 |
| 세션 TTL 구현 방식 | Guava Cache expireAfterAccess | Redis는 과함. 한 줄로 TTL 적용 가능 |
| 재고 부족 응답 이벤트 타입 | text (error 아님) | 고객이 읽을 수 있는 메시지. error는 시스템 장애용 |
| CASH_ON_DELIVERY 상태 전이 | order-service Order.create()에서 PAID 초기화 | pay-service를 거치지 않으므로 주문 생성 시점이 결제 확정 시점 |

### Phase 4c 완료 기준 달성

```
✅ ORDER_CONFIRM 감지 즉시 세션 리셋 → 중복 주문 생성 불가
✅ 30분 비활성 세션 자동 만료 → 메모리 누수 방지
✅ CASH_PREPAID 제거 → CARD / CASH_ON_DELIVERY만 지원
✅ CASH_ON_DELIVERY 주문 → 즉시 PAID + 배송 자동 트리거 (기존 구현 확인)
✅ 재고 부족 시 구체적 원인 SSE text 이벤트로 고객 안내
✅ CARD 선택 시 결제창 안내 문구 포함
```

---

## Phase 5 — 음성 입력

> Phase 4 완료 후. allmart-customer (Vue 3) 프론트엔드.

```
1. 마이크 버튼 UI 추가
2. Web Speech API 연동 (한국어, ko-KR)
3. 음성 인식 실패 시 텍스트 입력 폴백 자동 전환
4. 인식된 텍스트 화면 표시 (사용자 확인용)
5. (선택) TTS 응답 — Web Speech API synthesis
```

---

## Phase 6 — 평가

> Phase 4 완료 후 병행 가능.

### 평가 순서

```
1단계 (수동): 테스트 쿼리 20개 직접 실행, 눈으로 품질 확인
  - "달달한 간식 추천해줘"
  - "만원 이하 과일 있어요?"
  - "저번이랑 똑같이요"
  - "감귤 2개요" (단위 모호성)
  - "지난번 배송지로요"
  ... 등 20개

2단계 (자동): LLM 기반 세 지표 측정
  - 컨텍스트 관련성: 목표 0.70 이상
  - 답변 충실성:    목표 0.85 이상 (1순위)
  - 답변 관련성:    목표 0.80 이상 (2순위)

평가용 LLM: Claude Haiku 4.5 (비용 절감)
운영용 LLM: Claude Sonnet 4.6
```

---

## 기술 스택 요약

| 항목 | 선택 | 이유 |
|------|------|------|
| LLM 프레임워크 | Spring AI 1.0.0 GA | Spring Boot 3.x 생태계 통합. ChatClient/EmbeddingModel 추상화 |
| 운영 LLM | Claude Sonnet 4.6 | 한국어 품질, 스트리밍 안정 |
| 평가 LLM | Claude Haiku 4.5 | 비용 절감 (60~70%) |
| 임베딩 모델 | OpenAI text-embedding-3-small | 한국어 충분, Spring AI `EmbeddingModel` 기본 지원 |
| 벡터 저장소 | Elasticsearch 8.x | 이미 계획된 인프라, Hybrid Search |
| 음성 인식 | Web Speech API | 브라우저 내장, 무료, 한국어 지원 |
| 스트리밍 | SSE (Server-Sent Events) | Spring AI Flux 기본 지원 |
| chat/search-service Boot | 3.4.5 (타 서비스는 4.0.2) | Spring AI 1.x가 Spring Framework 6.x 타겟 |

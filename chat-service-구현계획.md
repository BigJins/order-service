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

## Phase 1 — chat-service 뼈대

> Phase 0 없이도 바로 시작 가능.
> 텍스트 기반 주문 대화가 먼저 동작하는 것을 확인 후 기능을 붙이는 방식.

### 생성할 것

```
신규 서비스: chat-service (포트 8094)
언어/프레임워크: Java 21, Spring Boot 4.x (기존 서비스와 동일)
```

### 구현 항목

```
1. Spring Boot 프로젝트 생성
   - spring-ai-anthropic-spring-boot-starter 의존성 추가
   - application.yml: port 8094, Claude API key 설정

2. Claude Sonnet 4.6 연동
   spring.ai.anthropic.chat.options.model=claude-sonnet-4-6

3. 다중 턴 대화 이력 관리
   - 최근 10턴 슬라이딩 윈도우 (토큰 비용 제어)
   - buyerId 기준 세션 관리

4. 시스템 프롬프트 작성
   - 페르소나: "allmart 마트 직원, 1~3문장, 친근한 말투"
   - Grounding: "[상품 목록]에 있는 것만 추천"
   - Few-Shot: 2~3개 예시 대화
   - Output Format: ##ORDER_CONFIRM## JSON 파싱

5. Prompt Engineering
   - 주문 의도 파악 ("감귤 세 개" → productName, quantity)
   - 결제 수단 수집 (카드/현금)
   - 배송지 수집 (신규 입력 or 이전 배송지)
   - 최종 확인 처리

6. 주문 생성 연결
   ##ORDER_CONFIRM## JSON 파싱 → POST /api/orders (order-service 재사용)

7. SSE 스트리밍
   Flux<String> → Server-Sent Events → Vue 3 EventSource
   목표: 첫 토큰 0.5초 이내 표시
```

### Phase 1 완료 기준

```
✅ "감귤 2개 주세요" 텍스트 입력
✅ 단위 모호성 시 되묻기 (Phase 0 없으면 스킵)
✅ 결제 수단, 배송지 수집 대화
✅ "제주 감귤 2박스 30,000원 맞으시죠?" 최종 확인
✅ POST /api/orders 호출 → tossOrderId 반환
✅ SSE 스트리밍으로 실시간 응답
```

---

## Phase 2 — Function Calling

> Phase 0 완료 후 chat-service에 Tool 등록.

### 구현할 Tool 3개

```java
// Tool 1: 상품 단위 조회 (Phase 0A 완료 후)
@Tool("getProductUnit")
ProductUnitInfo getProductUnit(Long productId) {
    // product-service GET /internal/products/{id}/price
    // unit, unitSize 반환
}

// Tool 2: 최근 주문 조회 (Phase 0B 완료 후)
@Tool("getRecentOrder")
RecentOrderInfo getRecentOrder(Long buyerId) {
    // order-query-service GET /api/orders/recent?buyerId={id}
    // 최근 주문 상품명, 수량, 배송지 반환
}

// Tool 3: 최근 배송지 조회 (Tool 2와 같은 API)
@Tool("getRecentDeliveryAddress")
DeliveryAddressInfo getRecentDeliveryAddress(Long buyerId) {
    // order-query-service 동일 API에서 배송지 필드만 추출
}
```

### Phase 2 완료 기준

```
✅ "저번이랑 똑같이요" → 최근 주문 자동 불러오기
✅ "감귤 2개요" → 단위 조회 → "박스 단위예요. 몇 박스?" 자동 되묻기
✅ "지난번 배송지로요" → 이전 배송지 자동 적용
```

---

## Phase 3 — ES + search-service

> Phase 2 완료 후. RAG의 선행 인프라.

### 구현 항목

```
1. docker-compose.yml에 ES 8.x 추가
   elasticsearch:8.13.0, 포트 9200
   xpack.security.enabled=false (로컬 개발)

2. search-service (8090) Spring Boot 신규 생성
   nori 한국어 형태소 분석기 설치
   products 인덱스 생성 (dense_vector 1536차원)

3. 상품 임베딩 파이프라인
   OpenAI text-embedding-3-small 연동
   embeddingText 포맷: "상품명: {name} 카테고리: {category} 설명: {desc} 단위: {unit} 가격: {price}원"
   초기 전체 색인: 배치 100개씩, 100ms 딜레이

4. 증분 색인
   product.registered.v1 Kafka 이벤트 수신 → ES upsert
   product.deleted.v1 수신 → ES 삭제

5. 재고 변경 처리
   inStock 필드 partial update (임베딩 재계산 없음)
```

### ES 인덱스 필드

```
productId, productName, categoryName, price, inStock,
unit, unitSize, updatedAt, embeddingText, embedding(1536)
```

---

## Phase 4 — RAG 상품 추천

> Phase 3 완료 후. chat-service에 추가.

### 구현 항목

```
1. ElasticsearchVectorStore 연결 (Spring AI)
2. Hybrid Search 쿼리
   kNN 벡터 검색 + BM25 키워드 검색 + RRF 결합
   사전 필터: inStock: true 항상 적용
3. 쿼리에서 카테고리/가격 추출 → 사전 필터 적용
4. Top-K: ES 후보 30개 → RRF 10개 → 유사도 0.6 필터 → LLM 전달 최대 5개
5. QuestionAnswerAdvisor (Spring AI) — 검색 결과 자동 프롬프트 주입
```

### Phase 4 완료 기준

```
✅ "달달한 간식 추천해줘" → ES 검색 → 상위 3~5개 추천
✅ "만원 이하 과일" → 가격 필터 + 벡터 검색
✅ 품절 상품은 추천에서 제외 (inStock 필터)
✅ 추천 후 "감귤로 할게요" → 주문 흐름 연결
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
| LLM 프레임워크 | Spring AI | 기존 Spring 생태계, 설정 1줄 |
| 운영 LLM | Claude Sonnet 4.6 | 한국어 품질, 스트리밍 안정 |
| 평가 LLM | Claude Haiku 4.5 | 비용 절감 (60~70%) |
| 임베딩 모델 | OpenAI text-embedding-3-small | 한국어 충분, Spring AI 기본 지원 |
| 벡터 저장소 | Elasticsearch 8.x | 이미 계획된 인프라, Hybrid Search |
| 음성 인식 | Web Speech API | 브라우저 내장, 무료, 한국어 지원 |
| 스트리밍 | SSE (Server-Sent Events) | Spring AI Flux 기본 지원 |

---

## 지금 당장 할 것

```
선택 A: Phase 0부터 (기존 서비스 수정 후 Phase 1)
  product-service unit/unitSize 추가
  order-query-service 최근 주문 API 추가
  → Function Calling까지 한 번에 완성

선택 B: Phase 1부터 바로 (권장)
  chat-service 뼈대 먼저 동작 확인
  텍스트 주문 대화가 돌아가는 것을 보면서 동기부여
  Phase 0 → Phase 2는 이후에 붙이기
```

/**
 * 주문 쓰기 + 읽기 혼합 부하 테스트
 *
 * 목적:
 *   - 실제 트래픽 패턴 재현: 쓰기 20% / 읽기 80%
 *   - 쓰기(POST /api/orders): DB INSERT + product/inventory 서비스 호출
 *   - 읽기(GET /api/orders/{orderId}): fetch join 쿼리 성능, N+1 없는지 확인
 *   - Loki: orderId 기준 로그 추적 가능한지 검증
 *   - Tempo: 느린 span (DB 쿼리, 외부 서비스 호출) 시각화
 *
 * 실행:
 *   k6 run k6/order-write-read-test.js
 *
 *   # 서비스 없이 order-service만 실행 시 (product/inventory mock 없으면 쓰기 실패)
 *   # → inventory-service, product-service 같이 띄우거나 아래 READ_ONLY=true 사용
 *   READ_ONLY=true k6 run k6/order-write-read-test.js
 *
 * Grafana 확인:
 *   - Loki: {app="order-service"} |= "orderId="  →  주문 생명주기 로그
 *   - Tempo: service=order-service  →  느린 span 확인
 *   - Prometheus: hikaricp_connections_active, http_server_requests_seconds
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const writeErrorRate = new Rate('write_error_rate');
const readErrorRate  = new Rate('read_error_rate');
const writeDuration  = new Trend('write_duration_ms', true);
const readDuration   = new Trend('read_duration_ms', true);
const ordersCreated  = new Counter('orders_created_total');

const BASE_URL  = __ENV.BASE_URL  || 'http://localhost:8081';
const READ_ONLY = __ENV.READ_ONLY === 'true';  // 읽기만 실행 (의존 서비스 없을 때)

// 부하 시나리오: 워밍업 → 기준 부하 → 스파이크 → 회복
export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 20,
      maxVUs: 40,
      startTime: '0s',
      tags: { phase: 'warmup' },
    },
    baseline: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 60,
      maxVUs: 150,
      startTime: '20s',
      tags: { phase: 'baseline' },
    },
    spike: {
      executor: 'constant-arrival-rate',
      rate: 400,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 200,
      maxVUs: 600,
      startTime: '80s',
      tags: { phase: 'spike' },
    },
    recovery: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '20s',
      preAllocatedVUs: 60,
      maxVUs: 150,
      startTime: '110s',
      tags: { phase: 'recovery' },
    },
  },
  thresholds: {
    'write_duration_ms{phase:baseline}': ['p(95)<500'],
    'read_duration_ms{phase:baseline}':  ['p(95)<100'],   // 읽기는 훨씬 빨라야 함
    'write_duration_ms{phase:spike}':    ['p(95)<1500'],
    'read_duration_ms{phase:spike}':     ['p(95)<300'],
    'write_error_rate':                  ['rate<0.05'],
    'read_error_rate':                   ['rate<0.01'],   // 읽기 에러는 더 엄격하게
  },
};

// stub 프로파일 사용 시 CASH_ON_DELIVERY만 사용 (pay-service 없이도 PAID 처리됨)
// 실제 서비스 환경이면 CARD도 추가 가능
const PAY_METHODS = ['CASH_ON_DELIVERY'];

// 가격은 StubProductServiceClient.PRICE_TABLE과 반드시 일치해야 함
const PRODUCTS = [
  { id: 1, name: '감귤',  price: 6000  },
  { id: 2, name: '딸기',  price: 7300  },
  { id: 3, name: '한라봉', price: 15000 },
];

const ZIP_CODES  = ['06234', '13494', '35235', '47011', '61011'];
const ROADS      = [
  '서울시 강남구 테헤란로 123',
  '서울시 마포구 합정로 45',
  '부산시 해운대구 센텀중앙로 79',
];

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

function buildOrderPayload() {
  const lineCount = Math.floor(Math.random() * 3) + 1;
  const products  = [...PRODUCTS].sort(() => Math.random() - 0.5).slice(0, lineCount);

  return JSON.stringify({
    buyerId: Math.floor(Math.random() * 100) + 1,
    payMethod: pick(PAY_METHODS),
    orderLines: products.map(p => ({
      productId:           p.id,
      productNameSnapshot: p.name,
      unitPrice:           p.price,
      quantity:            Math.floor(Math.random() * 3) + 1,
    })),
    deliverySnapshot: {
      zipCode:       pick(ZIP_CODES),
      roadAddress:   pick(ROADS),
      detailAddress: `${Math.floor(Math.random() * 999) + 1}호`,
    },
    martSnapshot: {
      martId:   1,
      martName: 'allmart 강남점',
      martPhone: '02-1234-5678',
    },
    orderMemo: Math.random() < 0.5 ? {
      orderRequest:    '덜 맵게 해주세요',
      deliveryRequest: '문 앞에 놓아주세요',
    } : null,
  });
}

const WRITE_HEADERS = { 'Content-Type': 'application/json' };

// 읽기용 orderId 공유 풀 (쓰기 성공 후 추가, 읽기에서 소비)
const createdOrderIds = [];

export default function () {
  // 쓰기 20% / 읽기 80% — READ_ONLY면 100% 읽기
  const doWrite = !READ_ONLY && Math.random() < 0.2;

  if (doWrite) {
    // ─── 쓰기: POST /api/orders ───────────────────────
    const res = http.post(`${BASE_URL}/api/orders`, buildOrderPayload(), {
      headers: WRITE_HEADERS,
      timeout: '10s',
    });

    const ok = res.status === 200 || res.status === 201;
    writeErrorRate.add(!ok);
    writeDuration.add(res.timings.duration);

    check(res, {
      '주문 생성 2xx':    () => ok,
      'tossOrderId 존재': () => {
        if (!ok) return true;
        try { return !!JSON.parse(res.body).tossOrderId; } catch { return false; }
      },
    });

    if (ok) {
      try {
        // Snowflake ID는 JS Number 정밀도(2^53) 초과 → JSON.parse 사용 안 함
        // regex로 raw body에서 orderId 문자열 추출
        const match = res.body.match(/"orderId"\s*:\s*(\d+)/);
        if (match) {
          createdOrderIds.push(match[1]);          // 문자열로 보관
          if (createdOrderIds.length > 1000) createdOrderIds.shift();
        }
        ordersCreated.add(1);
      } catch (_) {}
    }

  } else {
    // ─── 읽기: GET /api/orders/{orderId} ─────────────
    if (createdOrderIds.length === 0) {
      // 아직 생성된 주문이 없으면 목록 조회 fallback
      const res = http.get(`${BASE_URL}/api/orders`, { timeout: '5s' });
      readErrorRate.add(res.status !== 200);
      readDuration.add(res.timings.duration);
      check(res, { '목록 조회 200': (r) => r.status === 200 });
      return;
    }

    const orderId = createdOrderIds[Math.floor(Math.random() * createdOrderIds.length)];
    const res = http.get(`${BASE_URL}/api/orders/${orderId}`, { timeout: '5s' });

    const ok = res.status === 200;
    readErrorRate.add(!ok);
    readDuration.add(res.timings.duration);

    check(res, {
      '상세 조회 200':     () => ok,
      'orderId 일치':      () => {
        if (!ok) return true;
        try { return res.body.includes(`"orderId":${orderId}`) || res.body.includes(`"orderId": ${orderId}`); } catch { return false; }
      },
      'orderLines 존재':   () => {
        if (!ok) return true;
        try { return JSON.parse(res.body).orderLines?.length > 0; } catch { return false; }
      },
    });
  }
}

export function handleSummary(data) {
  const m = data.metrics;
  const pct = (metric, p) => metric?.values?.[`p(${p})`]?.toFixed(0) ?? 'N/A';
  const rate = (metric) => ((metric?.values?.rate ?? 0) * 100).toFixed(2);

  const passed = (key) => data.thresholds?.[key]?.ok ? '✅' : '❌';

  const summary = `
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  주문 쓰기 + 읽기 혼합 부하 테스트
  대상: ${BASE_URL}  /  READ_ONLY=${READ_ONLY}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  총 요청:      ${m.http_reqs?.values?.count ?? 0}건
  주문 생성 성공: ${m.orders_created_total?.values?.count ?? 0}건

  쓰기 응답시간 (POST /api/orders)
    P95: ${pct(m.write_duration_ms, 95)}ms  ${passed('write_duration_ms{phase:baseline}')}
    P99: ${pct(m.write_duration_ms, 99)}ms
  쓰기 에러율: ${rate(m.write_error_rate)}%  ${passed('write_error_rate')}

  읽기 응답시간 (GET /api/orders/{id})
    P95: ${pct(m.read_duration_ms, 95)}ms  ${passed('read_duration_ms{phase:baseline}')}
    P99: ${pct(m.read_duration_ms, 99)}ms
  읽기 에러율: ${rate(m.read_error_rate)}%  ${passed('read_error_rate')}

  ─────────────────────────────────────────
  Grafana에서 확인:
    Loki: {app="order-service"} |= "orderId="
    Tempo: service=order-service → 느린 span
    hikaricp_connections_active → 피크값
    http_server_requests_seconds_count → RPS
  ─────────────────────────────────────────
  읽기 P95가 100ms 초과 시 → N+1 문제 의심
    Tempo에서 DB span 개수 확인
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
`;
  console.log(summary);
  return { stdout: summary };
}

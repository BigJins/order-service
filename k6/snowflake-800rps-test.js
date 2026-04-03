/**
 * Snowflake ID 전환 + HikariCP pool-15 검증 — 800 RPS 지속 부하 테스트
 *
 * 목적:
 *   1. Snowflake ID로 전환 후 ID 충돌 없이 주문 생성 가능한지 검증
 *   2. HikariCP pool-size 25 → 15 축소 후에도 800 RPS에서 pending 0 유지 확인
 *   3. 배포 중 rolling update 시 응답 단절 없는지 모니터링
 *
 * 실행 방법:
 *   # 로컬 직접
 *   k6 run k6/snowflake-800rps-test.js
 *
 *   # 운영 엔드포인트 (Gateway 경유)
 *   BASE_URL=http://3.38.93.94:8000 AUTH_TOKEN=eyJ... k6 run k6/snowflake-800rps-test.js
 *
 *   # 운영 order-service 직접 (Gateway 우회, buyerId 바디로 전달)
 *   BASE_URL=http://3.38.93.94:8081 k6 run k6/snowflake-800rps-test.js
 *
 * Grafana 확인 지표:
 *   hikaricp_connections_active{pool="HikariPool-1"}   → 피크 ≤ 15 확인
 *   hikaricp_connections_pending{pool="HikariPool-1"}  → 0 유지
 *   hikaricp_connections_timeout_total                 → 0 유지
 *   jvm_gc_pause_seconds_max                           → GC 영향도 확인
 *
 * 부하 프로파일:
 *   0~30s:  ramp-up 0 → 800 RPS
 *   30~150s: 800 RPS 지속 (배포 rolling update 시간 커버)
 *   150~180s: ramp-down 800 → 0 RPS
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// ── 커스텀 메트릭 ─────────────────────────────────────────
const errorRate      = new Rate('order_error_rate');
const createTime     = new Trend('order_create_p95', true);
const ordersCreated  = new Counter('orders_created_total');
const ordersRejected = new Counter('orders_rejected_total');  // 4xx (재고 부족 등 정상 거절)
const serverErrors   = new Counter('orders_server_error_total'); // 5xx

// ── 환경 변수 ────────────────────────────────────────────
const BASE_URL    = __ENV.BASE_URL    || 'http://localhost:8081';
const AUTH_TOKEN  = __ENV.AUTH_TOKEN  || '';   // Gateway 경유 시 Bearer 토큰
const MACHINE_ID  = __ENV.MACHINE_ID  || '1';  // 로그 식별용 (실제 서비스는 SNOWFLAKE_MACHINE_ID 환경변수)

// ── 부하 시나리오 ─────────────────────────────────────────
export const options = {
  scenarios: {
    sustained_800: {
      executor: 'ramping-arrival-rate',
      startRate: 0,
      timeUnit: '1s',
      stages: [
        { duration: '30s',  target: 800 },  // ramp-up
        { duration: '120s', target: 800 },  // 지속 (배포 커버)
        { duration: '30s',  target: 0   },  // ramp-down
      ],
      preAllocatedVUs: 400,
      maxVUs: 1200,
    },
  },
  thresholds: {
    // pool-15 + Snowflake(5ms 트랜잭션) 기준 목표치
    'order_create_p95':       ['p(95)<500'],   // P95 500ms 이하
    'order_create_p95':       ['p(99)<1500'],  // P99 1500ms 이하
    'order_error_rate':       ['rate<0.01'],   // 5xx 에러율 1% 미만
    'orders_server_error_total': ['count<50'], // 5xx 절댓값 50건 미만
    'http_req_duration':      ['p(95)<600'],
  },
};

// ── 상품 풀 (재고 서비스가 있는 경우 실제 productId 사용) ──
const PRODUCTS = [
  { id: 1, name: '감귤',        price: 6000  },
  { id: 2, name: '딸기',        price: 7300  },
  { id: 3, name: '힘내세요!!!', price: 100000 },
];

const NAMES    = ['홍길동', '김철수', '이영희', '박민준', '최수진', '정예원', '강동현'];
const MEMOS    = ['문 앞에 놓아주세요', '경비실 맡겨주세요', null, null, null]; // null 비율 높게
const ZIP_CODES = ['06234', '13494', '35235', '47011', '61011', '48058', '42182'];
const ROADS    = [
  '서울시 강남구 테헤란로 123',
  '서울시 마포구 합정로 45',
  '부산시 해운대구 센텀중앙로 79',
  '인천시 연수구 컨벤시아대로 165',
  '대구시 수성구 달구벌대로 2311',
];

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function buildPayload() {
  // 1~3개 상품 랜덤 선택 (중복 없이)
  const shuffled = [...PRODUCTS].sort(() => Math.random() - 0.5);
  const lineCount = Math.floor(Math.random() * 3) + 1;
  const lines = shuffled.slice(0, lineCount).map(p => ({
    productId:           p.id,
    productNameSnapshot: p.name,
    unitPrice:           p.price,
    quantity:            Math.floor(Math.random() * 3) + 1,
  }));

  const zipIdx = Math.floor(Math.random() * ZIP_CODES.length);
  const memo   = pick(MEMOS);

  const body = {
    buyerId: 1,  // 실제 customer_id (tbl_customer)
    orderLines: lines,
    shippingInfo: {
      receiverName:  pick(NAMES),
      receiverPhone: '01012345678',
      address: {
        zipCode:       ZIP_CODES[zipIdx],
        roadAddress:   ROADS[zipIdx % ROADS.length],
        detailAddress: `${Math.floor(Math.random() * 999) + 1}호`,
      },
      ...(memo ? { deliveryMemo: memo } : {}),
    },
  };

  return JSON.stringify(body);
}

const HEADERS = {
  'Content-Type': 'application/json',
  ...(AUTH_TOKEN ? { 'Authorization': `Bearer ${AUTH_TOKEN}` } : {}),
};

// ── 메인 실행 ─────────────────────────────────────────────
export default function () {
  const res = http.post(`${BASE_URL}/api/orders`, buildPayload(), {
    headers: HEADERS,
    timeout: '10s',
  });

  const success = res.status === 200 || res.status === 201;
  const rejected = res.status >= 400 && res.status < 500;
  const serverErr = res.status >= 500;

  check(res, {
    '2xx 응답':        () => success,
    'tossOrderId 존재': (r) => {
      if (!success) return true; // 4xx는 체크 패스
      try { return !!JSON.parse(r.body).tossOrderId; } catch { return false; }
    },
    '응답시간 1s 이하': (r) => r.timings.duration < 1000,
  });

  createTime.add(res.timings.duration);
  errorRate.add(serverErr);

  if (success)   ordersCreated.add(1);
  if (rejected)  ordersRejected.add(1);
  if (serverErr) serverErrors.add(1);
}

// ── 종료 후 요약 ──────────────────────────────────────────
export function handleSummary(data) {
  const m    = data.metrics;
  const p95  = m.order_create_p95?.values?.['p(95)']?.toFixed(0) ?? 'N/A';
  const p99  = m.order_create_p95?.values?.['p(99)']?.toFixed(0) ?? 'N/A';
  const errPct = ((m.order_error_rate?.values?.rate ?? 0) * 100).toFixed(2);
  const created  = m.orders_created_total?.values?.count ?? 0;
  const rejected = m.orders_rejected_total?.values?.count ?? 0;
  const srv5xx   = m.orders_server_error_total?.values?.count ?? 0;
  const totalReqs = m.http_reqs?.values?.count ?? 0;
  const rps    = m.http_reqs?.values?.rate?.toFixed(1) ?? 'N/A';

  const passed = (tag) => data.thresholds?.[tag]?.ok ? '✅' : '❌';

  const summary = `
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Snowflake ID + HikariCP pool-15 — 800 RPS 검증
  대상: ${BASE_URL}  / machine-id: ${MACHINE_ID}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  총 요청:   ${totalReqs}건  (평균 ${rps} RPS)
  주문 생성: ${created}건
  정상 거절: ${rejected}건  (재고 부족 / 검증 실패 등)
  5xx 에러:  ${srv5xx}건

  응답시간 (주문 생성)
    P95: ${p95}ms   ${passed('order_create_p95')}
    P99: ${p99}ms

  5xx 에러율: ${errPct}%   ${passed('order_error_rate')}

  ─────────────────────────────────────────────
  Grafana에서 확인:
    hikaricp_connections_active  → 피크 ≤ 15 확인
    hikaricp_connections_pending → 0 유지 여부
    hikaricp_connections_timeout_total → 0 여부
  ─────────────────────────────────────────────
  DB에서 확인:
    SELECT COUNT(*), MIN(id), MAX(id) FROM orders;
    -- id가 181경대(1.81e18) 이상이면 Snowflake 정상 발급
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
`;

  console.log(summary);
  return { stdout: summary };
}

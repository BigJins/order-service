/**
 * MySQL 단일(1차) vs CQRS(현재) 직접 비교 부하 테스트
 *
 * 1차 테스트 기준선 (MySQL 단일, pool=3, 2026-04-04):
 *   읽기 P95: 9,990ms (타임아웃)  / 에러율: 97.02%
 *   쓰기 P95: 14,990ms (타임아웃) / 에러율: 97.37%
 *   주문 생성: ~400건 (추정)
 *
 * 실행:
 *   k6 run k6/mysql-vs-cqrs-compare.js
 *
 *   커스텀 타겟:
 *   WRITE_URL=http://10.0.1.100:8081 READ_URL=http://10.0.1.100:8092 \
 *   k6 run k6/mysql-vs-cqrs-compare.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const writeErrorRate = new Rate('write_error_rate');
const readErrorRate  = new Rate('read_error_rate');
const writeDuration  = new Trend('write_duration_ms', true);
const readDuration   = new Trend('read_duration_ms', true);
const ordersCreated  = new Counter('orders_created_total');
const ordersRejected = new Counter('orders_rejected_total');

const WRITE_URL = __ENV.WRITE_URL || 'http://10.0.1.100:8081';
const READ_URL  = __ENV.READ_URL  || 'http://10.0.1.100:8092';
const BUYER_ID  = __ENV.BUYER_ID  || '19731362701377792';

// 1차 테스트와 동일한 RPS 설정
export const options = {
  scenarios: {
    reads: {
      executor: 'constant-arrival-rate',
      rate: 800,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 400,
      maxVUs: 800,
      exec: 'readOrder',
      tags: { type: 'read' },
    },
    writes: {
      executor: 'constant-arrival-rate',
      rate: 150,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 100,
      maxVUs: 200,
      exec: 'writeOrder',
      tags: { type: 'write' },
    },
  },
  thresholds: {
    // 1차 기준선 대비 목표: 읽기 에러율 5% 미만, P95 500ms 이하
    'read_duration_ms':  ['p(95)<500'],
    'write_duration_ms': ['p(95)<5000'],
    'read_error_rate':   ['rate<0.05'],
    'write_error_rate':  ['rate<0.50'],
  },
};

const ZIP_CODES = ['06234', '13494', '35235', '47011', '61011'];
const ROADS = [
  '서울시 강남구 테헤란로 123',
  '서울시 마포구 합정로 45',
  '부산시 해운대구 센텀중앙로 79',
];

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

function buildOrderPayload(buyerId) {
  return `{
    "buyerId": ${buyerId},
    "payMethod": "CASH_ON_DELIVERY",
    "orderLines": [
      {
        "productId": 19719484977971456,
        "productNameSnapshot": "Orange",
        "unitPrice": 5000,
        "quantity": ${Math.floor(Math.random() * 3) + 1}
      }
    ],
    "deliverySnapshot": {
      "zipCode": "${pick(ZIP_CODES)}",
      "roadAddress": "${pick(ROADS)}",
      "detailAddress": "${Math.floor(Math.random() * 999) + 1}호"
    },
    "martSnapshot": {
      "martId": 1,
      "martName": "allmart 강남점",
      "martPhone": "02-1234-5678"
    }
  }`;
}

const WRITE_HEADERS = { 'Content-Type': 'application/json' };

// 1차 테스트: GET /api/orders (buyer 전체 목록, MySQL 풀스캔 → 97% 에러)
// 현재:       GET /api/orders/{orderId} (MongoDB _id 인덱스 O(1))
const ORDER_IDS = __ENV.ORDER_IDS
  ? __ENV.ORDER_IDS.split(',')
  : [
      '19734104831688960',
      '19734104828805376',
      '19734104826183936',
      '19734104799969536',
      '19734104798658816',
      '19734104793153792',
      '19734104770871552',
      '19734104770347264',
      '19734104760910080',
      '19734104745967872',
    ];

// ── 읽기: GET /api/orders/{orderId} → order-query-service → MongoDB ──
export function readOrder() {
  const orderId = ORDER_IDS[Math.floor(Math.random() * ORDER_IDS.length)];
  const res = http.get(`${READ_URL}/api/orders/${orderId}`, { timeout: '10s' });

  const ok = res.status === 200;
  readErrorRate.add(!ok);
  readDuration.add(res.timings.duration);
  check(res, { '읽기 200': () => ok });
}

// ── 쓰기: POST /api/orders → order-service → MySQL ──────────────────
export function writeOrder() {
  const res = http.post(`${WRITE_URL}/api/orders`, buildOrderPayload(BUYER_ID), {
    headers: WRITE_HEADERS,
    timeout: '15s',
  });

  const success   = res.status === 200 || res.status === 201;
  const rejected  = res.status >= 400 && res.status < 500;
  const serverErr = res.status >= 500;

  writeErrorRate.add(serverErr);
  writeDuration.add(res.timings.duration);
  check(res, { '쓰기 2xx': () => success });

  if (success)  ordersCreated.add(1);
  if (rejected) ordersRejected.add(1);
}

// ── 비교 요약 ─────────────────────────────────────────────────────────
export function handleSummary(data) {
  const m      = data.metrics;
  const pct    = (metric, p) => metric?.values?.[`p(${p})`]?.toFixed(0) ?? 'N/A';
  const rate   = (metric) => ((metric?.values?.rate ?? 0) * 100).toFixed(2);
  const cnt    = (metric) => metric?.values?.count ?? 0;
  const passed = (key) => data.thresholds?.[key]?.ok ? '✅' : '❌';

  // 1차 기준선 (MySQL 단일, pool=3)
  const BASE_READ_P95    = 9990;
  const BASE_READ_ERR    = 97.02;
  const BASE_WRITE_P95   = 14990;
  const BASE_WRITE_ERR   = 97.37;
  const BASE_ORDER_COUNT = 400; // 추정

  const curReadP95  = parseInt(pct(m.read_duration_ms, 95))  || 0;
  const curWriteP95 = parseInt(pct(m.write_duration_ms, 95)) || 0;
  const curReadErr  = parseFloat(rate(m.read_error_rate));
  const curWriteErr = parseFloat(rate(m.write_error_rate));
  const curOrders   = cnt(m.orders_created_total);

  const improve = (base, cur) => {
    if (base === 0) return 'N/A';
    const ratio = base / Math.max(cur, 1);
    return ratio >= 1 ? `${ratio.toFixed(1)}배 개선` : `${(1/ratio).toFixed(1)}배 악화`;
  };

  const summary = `
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  1차(MySQL 단일 pool=3) vs 현재(CQRS pool=10) 직접 비교
  읽기: ${READ_URL}  → order-query-service → MongoDB
  쓰기: ${WRITE_URL} → order-service → MySQL
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  총 요청:   ${cnt(m.http_reqs)}건
  주문 생성: ${curOrders}건  (1차 기준 ~${BASE_ORDER_COUNT}건)
  정상 거절: ${cnt(m.orders_rejected_total)}건

  ┌─────────────────────────────────────────────────────┐
  │  항목             │ 1차 기준선   │ 현재         │ 변화         │
  ├─────────────────────────────────────────────────────┤
  │  읽기 P95         │ ${String(BASE_READ_P95+'ms').padEnd(12)}│ ${String(pct(m.read_duration_ms,95)+'ms').padEnd(12)}│ ${improve(BASE_READ_P95, curReadP95).padEnd(12)}│
  │  읽기 에러율      │ ${String(BASE_READ_ERR+'%').padEnd(12)}│ ${String(rate(m.read_error_rate)+'%').padEnd(12)}│ ${improve(BASE_READ_ERR, curReadErr).padEnd(12)}│
  │  쓰기 P95         │ ${String(BASE_WRITE_P95+'ms').padEnd(12)}│ ${String(pct(m.write_duration_ms,95)+'ms').padEnd(12)}│ ${improve(BASE_WRITE_P95, curWriteP95).padEnd(12)}│
  │  쓰기 에러율      │ ${String(BASE_WRITE_ERR+'%').padEnd(12)}│ ${String(rate(m.write_error_rate)+'%').padEnd(12)}│ ${improve(BASE_WRITE_ERR, curWriteErr).padEnd(12)}│
  │  주문 생성        │ ~${String(BASE_ORDER_COUNT+'건').padEnd(11)}│ ${String(curOrders+'건').padEnd(12)}│              │
  └─────────────────────────────────────────────────────┘

  읽기 threshold  P95<500ms   ${passed('read_duration_ms')}   에러율<5%    ${passed('read_error_rate')}
  쓰기 threshold  P95<5000ms  ${passed('write_duration_ms')}   에러율<50%   ${passed('write_error_rate')}

  ※ 1차: GET /api/orders (MySQL 풀스캔) → 97% 에러
  ※ 현재: GET /api/orders/{orderId} (MongoDB _id 인덱스 O(1))
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
`;
  console.log(summary);
  return { stdout: summary };
}

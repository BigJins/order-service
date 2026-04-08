/**
 * CQRS 읽기/쓰기 분리 부하 테스트
 *
 * 목적:
 *   - 쓰기: POST /api/orders → order-service:8081 → MySQL
 *   - 읽기: GET  /api/orders → order-query-service:8092 → MongoDB
 *   - CQRS 분리 후 읽기 P95가 쓰기 부하에도 영향받지 않는지 검증
 *   - 2차 테스트(단일 MySQL, 읽기P95 3,920ms / 에러율 98.86%) 결과와 비교
 *
 * 실행:
 *   # 로컬 (기본 EC2 타겟)
 *   k6 run k6/cqrs-read-write-test.js
 *
 *   # 커스텀 타겟
 *   WRITE_URL=http://13.125.138.232:8081 READ_URL=http://13.125.138.232:8092 k6 run k6/cqrs-read-write-test.js
 *
 * Grafana 확인 지표 (http://52.79.176.193:3000):
 *   hikaricp_connections_active{pool="HikariPool-1"}   → order-service 피크
 *   hikaricp_connections_pending{pool="HikariPool-1"}  → 0 유지 여부
 *   http_server_requests_seconds_count{uri="/api/orders"} → order-query-service RPS
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

const WRITE_URL = __ENV.WRITE_URL || 'http://13.125.138.232:8081';
const READ_URL  = __ENV.READ_URL  || 'http://13.125.138.232:8092';
const BUYER_ID  = __ENV.BUYER_ID  || '19731362701377792';

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
    'read_duration_ms':  ['p(95)<500'],    // MongoDB 읽기: P95 500ms 이하 목표
    'write_duration_ms': ['p(95)<3000'],   // MySQL 쓰기: P95 3s 이하
    'read_error_rate':   ['rate<0.05'],    // 읽기 에러율 5% 미만
    'write_error_rate':  ['rate<0.20'],    // 쓰기 5xx 에러율 20% 미만
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
  // JSON 템플릿 문자열: Snowflake ID를 리터럴로 삽입하여 JS 정밀도 손실(2^53 초과) 방지
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

// 조회에 사용할 orderId 풀 (MongoDB _id 인덱스 — O(1) 단일 도큐먼트 조회)
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

// ── 읽기: GET /api/orders/{orderId} → order-query-service → MongoDB ────
export function readOrder() {
  const orderId = ORDER_IDS[Math.floor(Math.random() * ORDER_IDS.length)];
  const res = http.get(`${READ_URL}/api/orders/${orderId}`, {
    timeout: '5s',
  });

  const ok = res.status === 200;
  readErrorRate.add(!ok);
  readDuration.add(res.timings.duration);

  check(res, { '읽기 200': () => ok });
}

// ── 쓰기: POST /api/orders → order-service → MySQL ─────────────────────
export function writeOrder() {
  const res = http.post(`${WRITE_URL}/api/orders`, buildOrderPayload(BUYER_ID), {
    headers: WRITE_HEADERS,
    timeout: '15s',
  });

  const success  = res.status === 200 || res.status === 201;
  const rejected = res.status >= 400 && res.status < 500;
  const serverErr = res.status >= 500;

  writeErrorRate.add(serverErr);  // 5xx만 에러로 집계 (4xx는 재고부족 등 정상)
  writeDuration.add(res.timings.duration);

  check(res, { '쓰기 2xx': () => success });

  if (success)  ordersCreated.add(1);
  if (rejected) ordersRejected.add(1);
}

// ── 결과 요약 ──────────────────────────────────────────────────────────
export function handleSummary(data) {
  const m     = data.metrics;
  const pct   = (metric, p) => metric?.values?.[`p(${p})`]?.toFixed(0) ?? 'N/A';
  const rate  = (metric) => ((metric?.values?.rate ?? 0) * 100).toFixed(2);
  const cnt   = (metric) => metric?.values?.count ?? 0;
  const passed = (key) => data.thresholds?.[key]?.ok ? '✅' : '❌';

  const summary = `
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  CQRS 읽기/쓰기 분리 부하 테스트
  쓰기: ${WRITE_URL} (order-service → MySQL)
  읽기: ${READ_URL}  (order-query-service → MongoDB)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  총 요청:   ${cnt(m.http_reqs)}건
  주문 생성: ${cnt(m.orders_created_total)}건
  정상 거절: ${cnt(m.orders_rejected_total)}건 (재고부족/4xx)

  읽기 응답시간 (GET /api/orders/{id} → MongoDB _id 인덱스)
    P95: ${pct(m.read_duration_ms, 95)}ms   ${passed('read_duration_ms')}
    P99: ${pct(m.read_duration_ms, 99)}ms
  읽기 에러율: ${rate(m.read_error_rate)}%  ${passed('read_error_rate')}

  쓰기 응답시간 (POST /api/orders → MySQL)
    P95: ${pct(m.write_duration_ms, 95)}ms  ${passed('write_duration_ms')}
    P99: ${pct(m.write_duration_ms, 99)}ms
  쓰기 에러율(5xx): ${rate(m.write_error_rate)}%  ${passed('write_error_rate')}

  ─────────────────────────────────────────────
  2차 테스트(MySQL 단일 공유) 비교:
    읽기 P95: 3,920ms → ${pct(m.read_duration_ms, 95)}ms
    읽기 에러율: 98.86% → ${rate(m.read_error_rate)}%
  ─────────────────────────────────────────────
  Grafana: http://52.79.176.193:3000
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
`;
  console.log(summary);
  return { stdout: summary };
}

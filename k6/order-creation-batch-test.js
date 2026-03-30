/**
 * 주문 생성 Batch Insert 전후 비교 부하 테스트
 *
 * 목적:
 *   - Batch 적용 전: HikariCP 커넥션 고갈 확인
 *   - Batch 적용 후: active connections 안정화 확인
 *
 * 실행 방법:
 *   k6 run k6/order-creation-batch-test.js
 *
 * Grafana 확인 지표:
 *   hikaricp_connections_active   → 피크값 비교
 *   hikaricp_connections_pending  → 0 유지 여부
 *   hikaricp_connections_timeout_total → 타임아웃 0 유지 여부
 *
 * Hibernate SQL 로그 확인 (로컬 콘솔):
 *   Batch 적용 전: order_lines INSERT가 1건씩 N줄 출력
 *   Batch 적용 후: "batch size [50]" 통계 + INSERT 횟수 감소
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 커스텀 메트릭
const errorRate   = new Rate('order_error_rate');
const createTime  = new Trend('order_create_duration', true);
const ordersCreated = new Counter('orders_created_total');

const BASE_URL = 'http://localhost:8081';

// ── 부하 시나리오 ────────────────────────────────────────
export const options = {
  scenarios: {
    // 1단계: 워밍업 (50 RPS, 30초)
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 30,
      maxVUs: 60,
      startTime: '0s',
      tags: { phase: 'warmup' },
    },
    // 2단계: 기준 부하 (200 RPS, 60초)
    baseline: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 100,
      maxVUs: 200,
      startTime: '30s',
      tags: { phase: 'baseline' },
    },
    // 3단계: 스파이크 (800 RPS, 30초) — 풀 고갈 재현 구간
    spike: {
      executor: 'constant-arrival-rate',
      rate: 800,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 400,
      maxVUs: 1000,
      startTime: '90s',
      tags: { phase: 'spike' },
    },
    // 4단계: 회복 (200 RPS, 30초) — 커넥션 반환 확인
    recovery: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 100,
      maxVUs: 200,
      startTime: '120s',
      tags: { phase: 'recovery' },
    },
  },
  thresholds: {
    // Batch 적용 후 기준
    'order_create_duration{phase:baseline}': ['p(95)<300', 'p(99)<500'],
    'order_create_duration{phase:spike}':    ['p(95)<1000', 'p(99)<2000'],
    'order_error_rate':                      ['rate<0.05'],  // 에러율 5% 미만
  },
};

// ── 요청 바디 (OrderLine 3개 → INSERT 4회 → 배치 시 2회) ──
function buildOrderPayload() {
  // productId를 랜덤하게 변경해 DB unique 제약 회피
  const suffix = Math.floor(Math.random() * 100000);
  return JSON.stringify({
    buyerId: Math.floor(Math.random() * 1000) + 1,
    orderLines: [
      {
        productId: 1001,
        productNameSnapshot: `감귤 3kg (${suffix})`,
        unitPrice: { amount: 15000 },
        quantity: 2,
      },
      {
        productId: 1002,
        productNameSnapshot: `제주 한라봉 5kg (${suffix})`,
        unitPrice: { amount: 25000 },
        quantity: 1,
      },
      {
        productId: 1003,
        productNameSnapshot: `청귤 2kg (${suffix})`,
        unitPrice: { amount: 12000 },
        quantity: 3,
      },
    ],
    shippingInfo: {
      receiverName: '홍길동',
      receiverPhone: '01012345678',
      address: {
        zipCode: '12345',
        roadAddress: '서울시 강남구 테헤란로 123',
        detailAddress: `${suffix}호`,
      },
      deliveryMemo: '문 앞에 놓아주세요',
    },
  });
}

const HEADERS = { 'Content-Type': 'application/json' };

// ── 메인 실행 ────────────────────────────────────────────
export default function () {
  const start = Date.now();
  const res = http.post(`${BASE_URL}/api/orders`, buildOrderPayload(), { headers: HEADERS });
  const elapsed = Date.now() - start;

  const ok = check(res, {
    'status 200 or 201': (r) => r.status === 200 || r.status === 201,
    'has tossOrderId':   (r) => {
      try { return !!JSON.parse(r.body).tossOrderId; } catch { return false; }
    },
  });

  errorRate.add(!ok);
  createTime.add(elapsed);
  if (ok) ordersCreated.add(1);
}

// ── 테스트 종료 후 요약 출력 ─────────────────────────────
export function handleSummary(data) {
  const metrics = data.metrics;
  const p95 = (m) => m?.values?.['p(95)']?.toFixed(0) ?? 'N/A';
  const p99 = (m) => m?.values?.['p(99)']?.toFixed(0) ?? 'N/A';
  const rate = (m) => ((m?.values?.rate ?? 0) * 100).toFixed(2);

  const summary = `
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Batch Insert 전후 비교 — 주문 생성 부하 테스트
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  총 주문 생성 성공: ${metrics.orders_created_total?.values?.count ?? 0}건
  에러율:           ${rate(metrics.order_error_rate)}%

  응답시간 (전체)
    P95: ${p95(metrics.order_create_duration)}ms
    P99: ${p99(metrics.order_create_duration)}ms

  ─────────────────────────────────────────────
  Grafana에서 확인할 지표:
    hikaricp_connections_active{phase="spike"} 피크값
    hikaricp_connections_pending               0 유지 여부
  ─────────────────────────────────────────────
  Hibernate 통계 로그 (콘솔):
    "batch size [50]" 문구 확인
    order_lines INSERT 횟수 감소 확인
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
`;
  console.log(summary);
  return { stdout: summary };
}
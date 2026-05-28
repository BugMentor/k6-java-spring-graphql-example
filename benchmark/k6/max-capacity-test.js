// ==========================================================================
// MAXIMUM CAPACITY / BREAKING POINT TEST
// ==========================================================================
// Pushes the payment-service to its absolute limit. Starts with 2 pods
// (constitutional minimum, enforced by HPA minReplicas=2). Ramps VUs from
// 0 → 5,000 over 15 minutes, then holds 5,000 VUs for 45 minutes.
// Total test duration: 1 hour.
//
// HPA (Horizontal Pod Autoscaler) will attempt to scale pods from 2 → 30
// as CPU exceeds 80% and/or memory exceeds 60%. Even at 30 pods with 1Gi
// RAM each, the system is expected to collapse under 5,000 concurrent VUs.
// OOM kills and request timeouts are the expected outcome — the goal is to
// find the breaking point.
//
// Run: k6 run -e BASE_URL=http://localhost:30080 benchmark/k6/max-capacity-test.js
// ==========================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import {
  generateUUID,
  setupTestEntities,
  teardownTestEntities,
} from './_shared.js';

const errorRate = new Rate('errors');
const walletTransferLatency = new Trend('wallet_transfer_latency', true);
const paymentCreateLatency = new Trend('payment_create_latency', true);
const graphqlProcessPaymentLatency = new Trend('graphql_process_payment_latency', true);
const topUpLatency = new Trend('top_up_latency', true);
const searchLatency = new Trend('search_latency', true);
const concurrentConnections = new Counter('concurrent_vus');

const BASE_URL = __ENV.BASE_URL || 'http://payment-service.payments.svc.cluster.local';
const GRAPHQL_URL = __ENV.GRAPHQL_URL || `${BASE_URL}/graphql`;
const TARGET_VUS = parseInt(__ENV.TARGET_VUS) || 5000;
const RAMP_DURATION = __ENV.RAMP_DURATION || '15m';
const HOLD_DURATION = __ENV.HOLD_DURATION || '45m';

let userId = null;
let merchantId = null;
let walletId = null;

const GRAPHQL_PROCESS_PAYMENT_QUERY = `
mutation ProcessPayment($input: ProcessPaymentInput!) {
  processPayment(input: $input) {
    id
    amount
    type
    status
    createdAt
  }
}`;

export const options = {
  scenarios: {
    burn_it_down: {
      executor: 'ramping-vus',
      startVUs: 0,
      gracefulRampDown: '30s',
      stages: [
        { duration: RAMP_DURATION, target: TARGET_VUS },
        { duration: HOLD_DURATION, target: TARGET_VUS },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<5000', 'p(99)<10000'],
    http_req_failed: ['rate<0.10'],
    errors: ['rate<0.10'],
    wallet_transfer_latency: ['p(95)<6000'],
    payment_create_latency: ['p(95)<4000'],
    graphql_process_payment_latency: ['p(95)<8000'],
  },
  noConnectionReuse: false,
  batchPerHost: 20,
};

export function setup() {
  return setupTestEntities(BASE_URL, 'max-capacity');
}

export default function (data) {
  userId = data.userId;
  merchantId = data.merchantId;
  walletId = data.walletId;

  const scenario = Math.random();

  if (scenario < 0.35) {
    restWalletTransfer();
  } else if (scenario < 0.55) {
    restCreatePayment();
  } else if (scenario < 0.70) {
    graphqlProcessPayment();
  } else if (scenario < 0.80) {
    restTopUp();
  } else if (scenario < 0.90) {
    restSearch();
  } else {
    restGetPayment();
  }
}

function restWalletTransfer() {
  const amount = (Math.random() * 190 + 10).toFixed(2);
  const payload = JSON.stringify({
    walletId: walletId,
    merchantId: merchantId,
    amount: parseFloat(amount)
  });

  const start = Date.now();
  const res = http.post(`${BASE_URL}/v1/payments/wallet-transfer`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'wallet_transfer' }
  });

  walletTransferLatency.add(Date.now() - start);
  errorRate.add(res.status !== 200);
  concurrentConnections.add(1, { vus: __VU });

  check(res, {
    'wallet transfer status 200': r => r.status === 200,
  });
}

function restCreatePayment() {
  const amount = (Math.random() * 100 + 1).toFixed(2);
  const payload = JSON.stringify({
    userId: userId,
    merchantId: merchantId,
    amount: parseFloat(amount),
    type: 'DEBIT'
  });

  const start = Date.now();
  const res = http.post(`${BASE_URL}/v1/payments`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'create_payment' }
  });

  paymentCreateLatency.add(Date.now() - start);
  errorRate.add(res.status !== 201);

  check(res, {
    'create payment status 201': r => r.status === 201,
  });
}

function graphqlProcessPayment() {
  const amount = Math.random() * 50 + 1;
  const payload = JSON.stringify({
    query: GRAPHQL_PROCESS_PAYMENT_QUERY,
    variables: {
      input: {
        userId: userId,
        merchantId: merchantId,
        amount: parseFloat(amount.toFixed(2)),
        type: 'DEBIT'
      }
    }
  });

  const start = Date.now();
  const res = http.post(GRAPHQL_URL, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'graphql_process_payment' }
  });

  graphqlProcessPaymentLatency.add(Date.now() - start);
  errorRate.add(res.status !== 200);

  check(res, {
    'graphql payment status 200': r => r.status === 200,
  });
}

function restTopUp() {
  const amount = (Math.random() * 500 + 100).toFixed(2);

  const start = Date.now();
  const res = http.post(`${BASE_URL}/v1/payments/wallets/${walletId}/topup`, amount, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'top_up' }
  });

  topUpLatency.add(Date.now() - start);
  errorRate.add(res.status !== 200);

  check(res, {
    'top up status 200': r => r.status === 200,
  });
}

function restSearch() {
  const start = Date.now();
  const res = http.get(`${BASE_URL}/v1/payments/search?minAmount=1&maxAmount=500&status=SUCCESS&page=0&size=10`, {
    tags: { name: 'search_payments' }
  });

  searchLatency.add(Date.now() - start);
  errorRate.add(res.status !== 200);

  check(res, {
    'search status 200': r => r.status === 200,
  });
}

function restGetPayment() {
  const res = http.get(`${BASE_URL}/v1/payments/user/${userId}?status=SUCCESS&limit=10`, {
    tags: { name: 'get_user_payments' }
  });

  errorRate.add(res.status !== 200);
  check(res, {
    'get payments status 200': r => r.status === 200,
  });
}

export function teardown(data) {
  teardownTestEntities(BASE_URL, data);
}

export function handleSummary(data) {
  const m = data.metrics;
  const durationSec = (m.http_req_duration?.values?.count
    ? (data.state?.testRunDurationMs / 1000).toFixed(0)
    : 0);

  const summary = `
================================================================
  K6 TP STRESS RESULTS - Payment Service
================================================================
Target VUs:         ${TARGET_VUS}
Peak VUs:           ${m.vus_max?.values?.max || 0}
Duration:           ${durationSec}s
Ramp:               ${RAMP_DURATION} → ${HOLD_DURATION} hold

HTTP Metrics:
  Total Requests:      ${m.http_reqs?.values?.count || 0}
  Request Rate:        ${(m.http_reqs?.values?.rate || 0).toFixed(2)}/s
  Failed:              ${m.http_req_failed?.values?.passes || 0}
  Error Rate:          ${((m.http_req_failed?.values?.passes / (m.http_reqs?.values?.count || 1)) * 100).toFixed(2)}%
  Avg Duration:        ${(m.http_req_duration?.values?.avg || 0).toFixed(2)}ms
  P50:                 ${(m.http_req_duration?.values?.p(50) || 0).toFixed(2)}ms
  P95:                 ${(m.http_req_duration?.values?.p(95) || 0).toFixed(2)}ms
  P99:                 ${(m.http_req_duration?.values?.p(99) || 0).toFixed(2)}ms
  Max:                 ${(m.http_req_duration?.values?.max || 0).toFixed(2)}ms

Custom Metrics:
  Wallet Transfer P95: ${(m.wallet_transfer_latency?.values?.['p(95)'] || 0).toFixed(2)}ms
  Payment Create P95:  ${(m.payment_create_latency?.values?.['p(95)'] || 0).toFixed(2)}ms
  GraphQL Payment P95: ${(m.graphql_process_payment_latency?.values?.['p(95)'] || 0).toFixed(2)}ms
  Top Up P95:          ${(m.top_up_latency?.values?.['p(95)'] || 0).toFixed(2)}ms
  Search P95:          ${(m.search_latency?.values?.['p(95)'] || 0).toFixed(2)}ms
================================================================
`;

  return {
    'benchmark/k6/results/max-capacity_summary.json': JSON.stringify(data, null, 2),
    'benchmark/k6/results/max-capacity_summary.txt': summary,
    stdout: summary,
  };
}

// ==========================================================================
// CPU STRESS — HPA CPU SCALING TEST
// ==========================================================================
// Targets the CPU-based HPA (Horizontal Pod Autoscaler) threshold at 80%.
// HPA is a Kubernetes controller that automatically scales pod replicas
// based on observed resource utilization. This test drives CPU usage via
// batch operations, wallet transfers, GraphQL calls, and report generation
// to trigger the CPU > 80% scaling rule. Expect pods to scale from 2 to
// higher counts as VUs ramp.
//
// Default: 100 VUs, 8 ramp steps. Override with TARGET_VUS, SCALE_STEPS.
// Run: k6 run -e BASE_URL=http://localhost:30080 -e TARGET_VUS=500 benchmark/k6/cpu-stress-test.js
// ==========================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Gauge } from 'k6/metrics';
import {
  generateUUID,
  setupTestEntities,
  teardownTestEntities,
  buildRampStages,
  printScalingBox,
} from './_shared.js';

const errorRate = new Rate('cpu_stress_errors');
const walletTransferLatency = new Trend('cpu_stress_wallet_transfer_duration', true);
const batchCreateLatency = new Trend('cpu_stress_batch_duration', true);
const searchLatency = new Trend('cpu_stress_search_duration', true);
const activeVUs = new Gauge('cpu_stress_active_vus');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_VUS = parseInt(__ENV.TARGET_VUS) || 100;
const SCALE_STEPS = parseInt(__ENV.SCALE_STEPS) || 8;
const STEP_DURATION = __ENV.STEP_DURATION || '2m';
const COOLDOWN_DURATION = __ENV.COOLDOWN_DURATION || '3m';

let entityData;
let vusReached = 0;

export const options = {
  stages: buildRampStages(TARGET_VUS, SCALE_STEPS, STEP_DURATION, COOLDOWN_DURATION),
  thresholds: {
    'http_req_duration': ['p(95)<5000'],
    'cpu_stress_errors': ['rate<0.1'],
    'http_req_failed': ['rate<0.1'],
  },
  noConnectionReuse: false,
  batchPerHost: 6,
};

export function setup() {
  entityData = setupTestEntities(BASE_URL, 'cpu-stress');
  const { userId, merchantId, walletId } = entityData;

  console.log(`CPU STRESS: VUs=${TARGET_VUS} Steps=${SCALE_STEPS}`);
  console.log(`Expected HPA trigger at ~step ${Math.ceil(SCALE_STEPS * 0.6)}`);

  return entityData;
}

export default function(data) {
  const { userId, merchantId, walletId } = data;

  activeVUs.add(__VU);
  if (__VU > vusReached) vusReached = __VU;

  const scenario = __ITER % 10;

  switch (scenario) {
    case 0: case 1: case 2:
      cpuHeavyWalletTransfer(walletId, merchantId);
      break;
    case 3: case 4:
      cpuHeavyBatchCreate(userId, merchantId);
      break;
    case 5: case 6:
      cpuHeavySearch();
      break;
    case 7:
      cpuHeavyReport();
      break;
    case 8:
      cpuHeavyPaymentCreate(userId, merchantId);
      break;
    case 9:
      cpuHeavyGraphqlPayment(userId, merchantId);
      break;
  }

  if (__ITER % 100 === 0) {
    console.log(`[CPU VU ${__VU}] iter=${__ITER} peak=${vusReached}`);
  }
}

function cpuHeavyWalletTransfer(walletId, merchantId) {
  const amount = (Math.random() * 5 + 1).toFixed(2);

  const payloads = [];
  for (let i = 0; i < 3; i++) {
    payloads.push({
      method: 'POST',
      url: `${BASE_URL}/v1/payments/wallet-transfer`,
      body: JSON.stringify({ walletId, merchantId, amount: parseFloat(amount) }),
      params: { headers: { 'Content-Type': 'application/json' }, tags: { name: 'wallet_transfer_stress' } }
    });
  }

  const start = Date.now();
  const responses = http.batch(payloads);
  walletTransferLatency.add((Date.now() - start) / responses.length);

  for (const r of responses) {
    errorRate.add(r.status !== 200);
    check(r, { 'transfer ok': res => res.status === 200 });
  }
  sleep(0.1 + Math.random() * 0.2);
}

function cpuHeavyBatchCreate(userId, merchantId) {
  const batch = [];
  for (let i = 0; i < 10; i++) {
    batch.push({
      user: { id: userId },
      merchant: { id: merchantId },
      amount: Math.floor(Math.random() * 100) + 1,
      type: 'DEBIT',
      status: 'PENDING'
    });
  }

  const start = Date.now();
  const r = http.post(`${BASE_URL}/v1/payments/batch`, JSON.stringify(batch), {
    headers: { 'Content-Type': 'application/json' }, tags: { name: 'batch_create_stress' }
  });
  batchCreateLatency.add(Date.now() - start);

  errorRate.add(r.status !== 202);
  check(r, { 'batch accepted': res => res.status === 202 });
  sleep(0.3 + Math.random() * 0.3);
}

function cpuHeavySearch() {
  const queries = [
    `minAmount=1&maxAmount=500&status=SUCCESS&page=0&size=50`,
    `minAmount=100&maxAmount=1000&type=WALLET_TRANSFER&page=0&size=50`,
    `minAmount=1&maxAmount=50&status=PENDING&page=1&size=50`,
  ];
  const url = `${BASE_URL}/v1/payments/search?${queries[Math.floor(Math.random() * queries.length)]}`;

  const start = Date.now();
  const r = http.get(url, { tags: { name: 'search_stress' } });
  searchLatency.add(Date.now() - start);

  errorRate.add(r.status !== 200);
  check(r, { 'search ok': res => res.status === 200 });
  sleep(0.2 + Math.random() * 0.3);
}

function cpuHeavyReport() {
  const end = new Date();
  const start = new Date(end.getTime() - 7 * 24 * 60 * 60 * 1000);
  const r = http.get(
    `${BASE_URL}/v1/payments/reports/summary?startDate=${start.toISOString()}&endDate=${end.toISOString()}`,
    { tags: { name: 'report_stress' } }
  );
  errorRate.add(r.status !== 200);
  check(r, { 'report ok': res => res.status === 200 });
  sleep(0.4 + Math.random() * 0.3);
}

function cpuHeavyPaymentCreate(userId, merchantId) {
  const amount = (Math.random() * 200 + 10).toFixed(2);
  const r = http.post(`${BASE_URL}/v1/payments`, JSON.stringify({
    userId, merchantId, amount: parseFloat(amount), type: 'DEBIT'
  }), { headers: { 'Content-Type': 'application/json' }, tags: { name: 'payment_create_stress' } });

  if (r.status === 201) {
    const pid = r.json().id;
    sleep(0.1);
    const refund = http.put(`${BASE_URL}/v1/payments/${pid}/refund`, null, {
      headers: { 'Content-Type': 'application/json' }, tags: { name: 'refund_stress' }
    });
    errorRate.add(refund.status !== 200);
  }
  errorRate.add(r.status !== 201);
  sleep(0.2 + Math.random() * 0.2);
}

function cpuHeavyGraphqlPayment(userId, merchantId) {
  const amount = Math.random() * 50 + 1;
  const payload = JSON.stringify({
    query: `mutation ProcessPayment($input: ProcessPaymentInput!) { processPayment(input: $input) { id amount status } }`,
    variables: {
      input: {
        userId: userId,
        merchantId: merchantId,
        amount: parseFloat(amount.toFixed(2)),
        type: 'DEBIT'
      }
    }
  });

  const r = http.post(`${BASE_URL}/graphql`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'graphql_stress' }
  });
  errorRate.add(r.status !== 200);
  check(r, { 'graphql ok': res => res.status === 200 });
  sleep(0.4 + Math.random() * 0.3);
}

export function teardown(data) {
  teardownTestEntities(BASE_URL, data);
  console.log(`CPU STRESS COMPLETE. Peak VUs: ${vusReached}`);
}

export function handleSummary(data) {
  const m = data.metrics;
  return {
    'benchmark/k6/results/cpu-stress-summary.json': JSON.stringify(data, null, 2),
    stdout: printScalingBox('CPU STRESS TEST — SCALING RESULTS', {
      targetVUs: TARGET_VUS,
      maxVUs: m.vus_max?.values?.max || 0,
      totalReqs: m.http_reqs?.values?.count || 0,
      failedReqs: m.http_req_failed?.values?.passes || 0,
      avgDuration: m.http_req_duration?.values?.avg || 0,
      p95: m.http_req_duration?.values?.['p(95)'] || 0,
      p99: m.http_req_duration?.values?.['p(99)'] || 0,
      note: 'HPA > CPU 80% should trigger during ramp phase',
    }),
  };
}

// ==========================================================================
// MEMORY STRESS — HPA MEMORY SCALING + OOM TEST
// ==========================================================================
// Targets the memory-based HPA (Horizontal Pod Autoscaler) threshold at 60%.
// HPA is a Kubernetes controller that automatically scales pod replicas
// based on observed resource utilization. This test drives JVM heap usage
// via large batch operations, oversized payloads with metadata padding,
// concurrent holding patterns, and list-all queries. HPA triggers when
// average memory across pods exceeds 60% of the 1GiB constitutional limit.
// Sustained hold phase may trigger OOM (Out Of Memory) kills when pods
// exhaust their 1GiB RAM limit, visible in the OOM Kills Grafana panel.
//
// Default: 100 VUs, 5min hold. Override with TARGET_VUS, HOLD_DURATION.
// Run: k6 run -e BASE_URL=http://localhost:30080 -e TARGET_VUS=200 benchmark/k6/memory-stress-test.js
// ==========================================================================
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Gauge } from 'k6/metrics';
import {
  setupTestEntities,
  teardownTestEntities,
  printScalingBox,
} from './_shared.js';

const errorRate = new Rate('mem_stress_errors');
const largePayloadLatency = new Trend('mem_stress_large_payload_duration', true);
const concurrentHolding = new Trend('mem_stress_concurrent_holding', true);
const activeVUs = new Gauge('mem_stress_active_vus');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_VUS = parseInt(__ENV.TARGET_VUS) || 100;
const HOLD_DURATION = __ENV.HOLD_DURATION || '5m';
const BATCH_SIZE = parseInt(__ENV.BATCH_SIZE) || 100;

let vusReached = 0;

export const options = {
  stages: [
    { duration: '1m', target: Math.floor(TARGET_VUS * 0.3) },
    { duration: '2m', target: Math.floor(TARGET_VUS * 0.7) },
    { duration: '1m', target: TARGET_VUS },
    { duration: HOLD_DURATION, target: TARGET_VUS },
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<10000'],
    'mem_stress_errors': ['rate<0.15'],
    'http_req_failed': ['rate<0.15'],
  },
  noConnectionReuse: false,
  batchPerHost: 4,
};

export function setup() {
  const data = setupTestEntities(BASE_URL, 'mem-stress');

  const largeBatch = [];
  for (let i = 0; i < BATCH_SIZE; i++) {
    largeBatch.push({
      user: { id: data.userId },
      merchant: { id: data.merchantId },
      amount: Math.floor(Math.random() * 100) + 1,
      type: 'DEBIT',
      status: 'PENDING'
    });
  }

  console.log(`MEMORY STRESS: VUs=${TARGET_VUS} Batch=${BATCH_SIZE}`);
  console.log(`Expected HPA trigger: memory > 60% during sustained VU hold`);

  return { ...data, largeBatch };
}

export default function(data) {
  const { userId, merchantId, walletId } = data;
  const batch = data.largeBatch.slice(0, Math.floor(Math.random() * 30 + 10));

  activeVUs.add(__VU);
  if (__VU > vusReached) vusReached = __VU;

  switch (__ITER % 6) {
    case 0: case 1:
      memoryHeavyBatch(batch);
      break;
    case 2:
      memoryHeavyLargePayload(userId, merchantId);
      break;
    case 3:
      memoryHeavyConcurrentHolding(walletId, merchantId);
      break;
    case 4:
      memoryHeavyListAll(userId);
      break;
    case 5:
      memoryHeavyTopUp(walletId);
      break;
  }

  if (__ITER % 50 === 0) {
    console.log(`[MEM VU ${__VU}] iter=${__ITER} pressure=${((__VU/TARGET_VUS)*100).toFixed(0)}%`);
  }
}

function memoryHeavyBatch(batch) {
  const start = Date.now();
  const r = http.post(`${BASE_URL}/v1/payments/batch`, JSON.stringify(batch), {
    headers: { 'Content-Type': 'application/json' }, tags: { name: 'mem_batch' }
  });
  largePayloadLatency.add(Date.now() - start);
  errorRate.add(r.status !== 202);
  check(r, { 'batch ok': res => res.status === 202 });
  sleep(0.5 + Math.random() * 0.5);
}

function memoryHeavyLargePayload(userId, merchantId) {
  const payload = {
    userId, merchantId,
    amount: 999999.99, type: 'DEBIT',
    metadata: 'X'.repeat(1024)
  };
  const start = Date.now();
  const r = http.post(`${BASE_URL}/v1/payments`, JSON.stringify(payload), {
    headers: { 'Content-Type': 'application/json' }, tags: { name: 'mem_large_payload' }
  });
  largePayloadLatency.add(Date.now() - start);
  errorRate.add(r.status !== 201 && r.status !== 400);
  check(r, { 'payload handled': res => res.status === 201 || res.status === 400 });
  sleep(0.3 + Math.random() * 0.4);
}

function memoryHeavyConcurrentHolding(walletId, merchantId) {
  const amount = (Math.random() * 10 + 1).toFixed(2);
  const payloads = [];
  for (let i = 0; i < 8; i++) {
    payloads.push({
      method: 'POST',
      url: `${BASE_URL}/v1/payments/wallet-transfer`,
      body: JSON.stringify({ walletId, merchantId, amount: parseFloat(amount) }),
      params: { headers: { 'Content-Type': 'application/json' }, tags: { name: 'mem_concurrent' } }
    });
  }
  const start = Date.now();
  const responses = http.batch(payloads);
  concurrentHolding.add(Date.now() - start);
  for (const r of responses) {
    errorRate.add(r.status !== 200);
    check(r, { 'transfer ok': res => res.status === 200 });
  }
  sleep(0.5 + Math.random() * 0.5);
}

function memoryHeavyListAll(userId) {
  const endpoints = [
    `/v1/users`,
    `/v1/merchants`,
    `/v1/wallets`,
    `/v1/payments/user/${userId}?status=SUCCESS&limit=100`,
    `/v1/payments/user/${userId}?status=PENDING&limit=100`,
  ];
  const r = http.get(`${BASE_URL}${endpoints[Math.floor(Math.random() * endpoints.length)]}`, {
    tags: { name: 'mem_list_all' }
  });
  errorRate.add(r.status !== 200);
  check(r, { 'list ok': res => res.status === 200 });
  sleep(0.2 + Math.random() * 0.3);
}

function memoryHeavyTopUp(walletId) {
  const amounts = ['100.00', '500.00', '1000.00', '5000.00'];
  const amount = amounts[Math.floor(Math.random() * amounts.length)];
  const r = http.post(`${BASE_URL}/v1/payments/wallets/${walletId}/topup`, amount, {
    headers: { 'Content-Type': 'application/json' }, tags: { name: 'mem_topup' }
  });
  errorRate.add(r.status !== 200);
  check(r, { 'topup ok': res => res.status === 200 });
  sleep(0.1 + Math.random() * 0.2);
}

export function teardown(data) {
  teardownTestEntities(BASE_URL, data);
  console.log(`MEMORY STRESS COMPLETE. Peak VUs: ${vusReached}`);
}

export function handleSummary(data) {
  const m = data.metrics;
  return {
    'benchmark/k6/results/mem-stress-summary.json': JSON.stringify(data, null, 2),
    stdout: printScalingBox('MEMORY STRESS TEST — SCALING RESULTS', {
      targetVUs: TARGET_VUS,
      maxVUs: m.vus_max?.values?.max || 0,
      totalReqs: m.http_reqs?.values?.count || 0,
      failedReqs: m.http_req_failed?.values?.passes || 0,
      avgDuration: m.http_req_duration?.values?.avg || 0,
      p95: m.http_req_duration?.values?.['p(95)'] || 0,
      p99: m.http_req_duration?.values?.['p(99)'] || 0,
      note: 'HPA > Memory 60% should trigger during hold phase',
    }),
  };
}

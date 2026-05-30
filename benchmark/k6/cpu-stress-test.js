import { check, sleep } from 'k6';
import http from 'k6/http';
import { Rate, Trend } from 'k6/metrics';
import {
  generateUUID,
  setupTestEntities,
  teardownTestEntities,
} from './_shared.js';

const errorRate = new Rate('errors');
const walletTransferLatency = new Trend('cpu_stress_wallet_transfer_duration', true);
const batchLatency = new Trend('cpu_stress_batch_duration', true);
const searchLatency = new Trend('cpu_stress_search_duration', true);
const summaryLatency = new Trend('cpu_stress_summary_duration', true);
const paymentCreateLatency = new Trend('cpu_stress_payment_create_duration', true);

const BASE_URL = __ENV.BASE_URL || 'http://payment-service.payments.svc.cluster.local';
const GRAPHQL_URL = `${BASE_URL}/graphql`;
const TARGET_VUS = parseInt(__ENV.TARGET_VUS) || 300;

const WALLET_TRANSFER_MUTATION = `
mutation($walletId: String!, $merchantId: String!, $amount: Float!) {
  walletTransfer(walletId: $walletId, merchantId: $merchantId, amount: $amount) {
    id
    status
  }
}`;

const BATCH_MUTATION = `
mutation($payments: [ProcessPaymentInput!]!) {
  processBatchPayments(payments: $payments)
}`;

const SUMMARY_QUERY = `
query($startDate: String!, $endDate: String!) {
  paymentSummary(startDate: $startDate, endDate: $endDate) {
    totalsByStatus {
      status
      total
    }
  }
}`;

const PROCESS_PAYMENT_MUTATION = `
mutation($input: ProcessPaymentInput!) {
  processPayment(input: $input) {
    id
    status
  }
}`;

export const options = {
  stages: [
    { duration: '1m', target: Math.floor(TARGET_VUS * 0.2) },
    { duration: '2m', target: Math.floor(TARGET_VUS * 0.5) },
    { duration: '3m', target: TARGET_VUS },
    { duration: '5m', target: TARGET_VUS },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<8000', 'p(99)<20000'],
    http_req_failed: ['rate<0.10'],
  },
  noConnectionReuse: false,
  batchPerHost: 20,
};

export function setup() {
  return setupTestEntities(BASE_URL, 'cpu');
}

function graphql(query, variables, tags) {
  return http.post(GRAPHQL_URL, JSON.stringify({ query, variables }), {
    headers: { 'Content-Type': 'application/json' },
    tags,
  });
}

export default function (data) {
  const walletId = data.walletId;
  const merchantId = data.merchantId;
  const userId = data.userId;

  const scenario = Math.random();

  if (scenario < 0.30) {
    const start = Date.now();
    const responses = [];
    for (let i = 0; i < 3; i++) {
      const amount = (Math.random() * 100 + 0.01).toFixed(2);
      const res = graphql(WALLET_TRANSFER_MUTATION, {
        walletId, merchantId, amount: parseFloat(amount),
      }, { name: 'wallet_transfer' });
      responses.push(res);
    }
    walletTransferLatency.add((Date.now() - start) / responses.length);
    responses.forEach(r => {
      errorRate.add(r.status !== 200);
      check(r, { 'wallet transfer ok': res => res.status === 200 });
    });

  } else if (scenario < 0.55) {
    const payments = [{
      userId, merchantId,
      amount: (Math.random() * 100 + 0.01).toFixed(2),
      type: 'DEBIT',
    }];
    const start = Date.now();
    const res = graphql(BATCH_MUTATION, { payments }, { name: 'batch' });
    batchLatency.add(Date.now() - start);
    errorRate.add(res.status !== 200);
    check(res, { 'batch ok': r => r.status === 200 });

  } else if (scenario < 0.75) {
    const res = graphql(SEARCH_QUERY, {
      minAmount: 1, maxAmount: 500, status: 'SUCCESS', page: 0, size: 10,
    }, { name: 'search' });
    searchLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'search ok': r => r.status === 200 });

  } else if (scenario < 0.90) {
    const res = graphql(SUMMARY_QUERY, {
      startDate: '2020-01-01T00:00:00Z',
      endDate: '2030-12-31T23:59:59Z',
    }, { name: 'summary' });
    summaryLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'summary ok': r => r.status === 200 });
  } else {
    const res = graphql(PROCESS_PAYMENT_MUTATION, {
      input: {
        userId, merchantId,
        amount: (Math.random() * 200 + 0.01).toFixed(2),
        type: 'DEBIT',
      },
    }, { name: 'process_payment' });
    paymentCreateLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'payment ok': r => r.status === 200 });
  }
}

export function teardown(data) {
  teardownTestEntities(BASE_URL, data);
}

export function handleSummary(data) {
  return {
    'benchmark/k6/results/java-graphql-cpu-stress-summary.json': JSON.stringify(data, null, 2),
  };
}

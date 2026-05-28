import { check, sleep, group } from 'k6';
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
const GRAPHQL_URL = `${BASE_URL}/graphql`;
const TARGET_VUS = parseInt(__ENV.TARGET_VUS) || 50;
const TEST_DURATION = __ENV.TEST_DURATION || '5m';

const WALLET_TRANSFER_MUTATION = `
mutation($walletId: String!, $merchantId: String!, $amount: Float!) {
  walletTransfer(walletId: $walletId, merchantId: $merchantId, amount: $amount) {
    id
    status
    amount
  }
}`;

const PROCESS_PAYMENT_MUTATION = `
mutation($input: ProcessPaymentInput!) {
  processPayment(input: $input) {
    id
    amount
    type
    status
    createdAt
  }
}`;

const TOP_UP_MUTATION = `
mutation($walletId: String!, $amount: Float!) {
  topUpWallet(walletId: $walletId, amount: $amount) {
    id
    balance
  }
}`;

const SEARCH_QUERY = `
query($minAmount: Float, $maxAmount: Float, $status: String, $page: Int, $size: Int) {
  searchPayments(minAmount: $minAmount, maxAmount: $maxAmount, status: $status, page: $page, size: $size) {
    id
    amount
    status
    createdAt
  }
}`;

const USER_PAYMENTS_QUERY = `
query($userId: String!, $status: String, $limit: Int) {
  payments(userId: $userId, status: $status, limit: $limit) {
    id
    amount
    status
    createdAt
  }
}`;

export const options = {
  stages: [
    { duration: '1m',  target: Math.floor(TARGET_VUS * 0.2) },
    { duration: '2m',  target: Math.floor(TARGET_VUS * 0.5) },
    { duration: '2m',  target: Math.floor(TARGET_VUS * 0.8) },
    { duration: '3m',  target: TARGET_VUS },
    { duration: '5m',  target: TARGET_VUS },
    { duration: '2m',  target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<2000', 'p(99)<5000'],
    http_req_failed: ['rate<0.05'],
    wallet_transfer_latency: ['p(95)<3000'],
    payment_create_latency: ['p(95)<2000'],
    graphql_process_payment_latency: ['p(95)<4000'],
  },
  noConnectionReuse: false,
  batchPerHost: 20,
};

export function setup() {
  return setupTestEntities(BASE_URL, 'load');
}

export default function (data) {
  const walletId = data.walletId;
  const merchantId = data.merchantId;
  const userId = data.userId;

  const scenario = Math.random();

  if (scenario < 0.35) {
    graphqlWalletTransfer(walletId, merchantId);
  } else if (scenario < 0.55) {
    graphqlCreatePayment(userId, merchantId);
  } else if (scenario < 0.70) {
    graphqlProcessPayment(userId, merchantId);
  } else if (scenario < 0.80) {
    graphqlTopUp(walletId);
  } else if (scenario < 0.90) {
    graphqlSearch();
  } else {
    graphqlGetUserPayments(userId);
  }
}

function graphqlWalletTransfer(walletId, merchantId) {
  const amount = (Math.random() * 190 + 10).toFixed(2);
  const payload = JSON.stringify({
    query: WALLET_TRANSFER_MUTATION,
    variables: {
      walletId,
      merchantId,
      amount: parseFloat(amount),
    },
  });

  const start = Date.now();
  const res = http.post(GRAPHQL_URL, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'wallet_transfer' },
  });

  walletTransferLatency.add(Date.now() - start);
  errorRate.add(res.status !== 200);
  concurrentConnections.add(1, { vus: __VU });

  check(res, {
    'wallet transfer graphql ok': r => r.status === 200,
  });
}

function graphqlCreatePayment(userId, merchantId) {
  const amount = (Math.random() * 100 + 1).toFixed(2);
  const payload = JSON.stringify({
    query: PROCESS_PAYMENT_MUTATION,
    variables: {
      input: {
        userId,
        merchantId,
        amount: parseFloat(amount),
        type: 'DEBIT',
      },
    },
  });

  const start = Date.now();
  const res = http.post(GRAPHQL_URL, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'create_payment' },
  });

  paymentCreateLatency.add(Date.now() - start);
  errorRate.add(res.status !== 200);

  check(res, {
    'create payment graphql ok': r => r.status === 200,
  });
}

function graphqlProcessPayment(userId, merchantId) {
  const amount = Math.random() * 50 + 1;
  const payload = JSON.stringify({
    query: PROCESS_PAYMENT_MUTATION,
    variables: {
      input: {
        userId,
        merchantId,
        amount: parseFloat(amount.toFixed(2)),
        type: 'DEBIT',
      },
    },
  });

  const start = Date.now();
  const res = http.post(GRAPHQL_URL, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'graphql_process_payment' },
  });

  graphqlProcessPaymentLatency.add(Date.now() - start);
  errorRate.add(res.status !== 200);

  check(res, {
    'graphql payment graphql ok': r => r.status === 200,
  });
}

function graphqlTopUp(walletId) {
  const amount = (Math.random() * 500 + 100).toFixed(2);
  const payload = JSON.stringify({
    query: TOP_UP_MUTATION,
    variables: {
      walletId,
      amount: parseFloat(amount),
    },
  });

  const start = Date.now();
  const res = http.post(GRAPHQL_URL, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'top_up' },
  });

  topUpLatency.add(Date.now() - start);
  errorRate.add(res.status !== 200);

  check(res, {
    'top up graphql ok': r => r.status === 200,
  });
}

function graphqlSearch() {
  const payload = JSON.stringify({
    query: SEARCH_QUERY,
    variables: { minAmount: 1, maxAmount: 500, status: 'SUCCESS', page: 0, size: 10 },
  });

  const start = Date.now();
  const res = http.post(GRAPHQL_URL, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'search_payments' },
  });

  searchLatency.add(Date.now() - start);
  errorRate.add(res.status !== 200);

  check(res, {
    'search graphql ok': r => r.status === 200,
  });
}

function graphqlGetUserPayments(userId) {
  const payload = JSON.stringify({
    query: USER_PAYMENTS_QUERY,
    variables: { userId, status: 'SUCCESS', limit: 10 },
  });

  const start = Date.now();
  const res = http.post(GRAPHQL_URL, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'get_user_payments' },
  });

  searchLatency.add(Date.now() - start);
  errorRate.add(res.status !== 200);

  check(res, {
    'get payments graphql ok': r => r.status === 200,
  });
}

export function teardown(data) {
  teardownTestEntities(BASE_URL, data);
}

export function handleSummary(data) {
  return {
    'benchmark/k6/results/summary.json': JSON.stringify(data, null, 2),
    'benchmark/k6/results/summary.txt': textSummary(data),
  };
}

function textSummary(data) {
  return `
============================================================
  K6 LOAD TEST RESULTS - Payment Service (GraphQL Only)
============================================================
Target VUs: ${TARGET_VUS}
Duration: ${TEST_DURATION}
Base URL: ${BASE_URL}

HTTP Metrics:
  Total Requests:      ${data.metrics.http_reqs?.values?.count || 0}
  Failed Requests:     ${data.metrics.http_req_failed?.values?.passes || 0}
  Error Rate:          ${((data.metrics.errors?.values?.rate || 0) * 100).toFixed(2)}%
  Avg Response Time:   ${(data.metrics.http_req_duration?.values?.avg || 0).toFixed(2)}ms
  P50:                 ${(data.metrics.http_req_duration?.values?.p(50) || 0).toFixed(2)}ms
  P95:                 ${(data.metrics.http_req_duration?.values?.p(95) || 0).toFixed(2)}ms
  P99:                 ${(data.metrics.http_req_duration?.values?.p(99) || 0).toFixed(2)}ms
  Max:                 ${(data.metrics.http_req_duration?.values?.max || 0).toFixed(2)}ms

Custom Metrics:
  Wallet Transfer P95: ${(data.metrics.wallet_transfer_latency?.values?.['p(95)'] || 0).toFixed(2)}ms
  Payment Create P95:  ${(data.metrics.payment_create_latency?.values?.['p(95)'] || 0).toFixed(2)}ms
  GraphQL Payment P95: ${(data.metrics.graphql_process_payment_latency?.values?.['p(95)'] || 0).toFixed(2)}ms
  Top Up P95:          ${(data.metrics.top_up_latency?.values?.['p(95)'] || 0).toFixed(2)}ms
  Search P95:          ${(data.metrics.search_latency?.values?.['p(95)'] || 0).toFixed(2)}ms

Peak VUs:              ${data.metrics.vus_max?.values?.max || 0}
Peak Iterations/s:     ${(data.metrics.iterations?.values?.rate || 0).toFixed(2)}
============================================================
`;
}

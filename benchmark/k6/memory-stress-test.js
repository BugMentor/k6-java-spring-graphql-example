import { check, sleep } from 'k6';
import http from 'k6/http';
import { Rate, Trend } from 'k6/metrics';
import {
  generateUUID,
  setupTestEntities,
  teardownTestEntities,
} from './_shared.js';

const errorRate = new Rate('errors');
const walletTransferLatency = new Trend('wallet_transfer_latency', true);
const batchLatency = new Trend('batch_latency', true);
const topUpLatency = new Trend('top_up_latency', true);
const searchLatency = new Trend('search_latency', true);

const BASE_URL = __ENV.BASE_URL || 'http://payment-service.payments.svc.cluster.local';
const GRAPHQL_URL = `${BASE_URL}/graphql`;
const TARGET_VUS = parseInt(__ENV.TARGET_VUS) || 100;

const WALLET_TRANSFER_MUTATION = `
mutation($walletId: String!, $merchantId: String!, $amount: Float!) {
  walletTransfer(walletId: $walletId, merchantId: $merchantId, amount: $amount) {
    id
    status
  }
}`;

const TOP_UP_MUTATION = `
mutation($walletId: String!, $amount: Float!) {
  topUpWallet(walletId: $walletId, amount: $amount) {
    id
    balance
  }
}`;

const USER_PAYMENTS_QUERY = `
query($userId: String!, $status: String, $limit: Int) {
  payments(userId: $userId, status: $status, limit: $limit) {
    id
    status
    amount
  }
}`;

const SEARCH_QUERY = `
query($minAmount: Float, $maxAmount: Float, $status: String, $page: Int, $size: Int) {
  searchPayments(minAmount: $minAmount, maxAmount: $maxAmount, status: $status, page: $page, size: $size) {
    id
    amount
    status
  }
}`;

const BATCH_MUTATION = `
mutation($payments: [ProcessPaymentInput!]!) {
  processBatchPayments(payments: $payments)
}`;

export const options = {
  stages: [
    { duration: '30s', target: Math.floor(TARGET_VUS * 0.2) },
    { duration: '1m', target: Math.floor(TARGET_VUS * 0.5) },
    { duration: '2m', target: TARGET_VUS },
    { duration: '5m', target: TARGET_VUS },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<10000', 'p(99)<30000'],
    http_req_failed: ['rate<0.10'],
  },
  noConnectionReuse: false,
  batchPerHost: 20,
};

export function setup() {
  return setupTestEntities(BASE_URL, 'memory');
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

  if (scenario < 0.35) {
    for (let i = 0; i < 8; i++) {
      const amount = (Math.random() * 100 + 0.01).toFixed(2);
      const res = graphql(WALLET_TRANSFER_MUTATION, {
        walletId, merchantId, amount: parseFloat(amount),
      }, { name: 'wallet_transfer' });
      walletTransferLatency.add(Date.now() - res.timings.duration);
      errorRate.add(res.status !== 200);
      check(res, { 'wallet transfer ok': r => r.status === 200 });
    }

  } else if (scenario < 0.60) {
    const payments = [];
    for (let i = 0; i < 20; i++) {
      payments.push({
        userId, merchantId,
        amount: (Math.random() * 100 + 0.01).toFixed(2),
        type: 'DEBIT',
      });
    }
    const res = graphql(BATCH_MUTATION, { payments }, { name: 'batch' });
    batchLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'batch ok': r => r.status === 200 });

  } else if (scenario < 0.80) {
    const amount = (Math.random() * 5000 + 1).toFixed(2);
    const res = graphql(TOP_UP_MUTATION, {
      walletId, amount: parseFloat(amount),
    }, { name: 'top_up' });
    topUpLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'topup ok': r => r.status === 200 });

  } else if (scenario < 0.90) {
    const res = graphql(USER_PAYMENTS_QUERY, {
      userId, status: 'SUCCESS', limit: 100,
    }, { name: 'user_payments' });
    searchLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'user payments ok': r => r.status === 200 });

  } else {
    const res = graphql(SEARCH_QUERY, {
      minAmount: 10, maxAmount: 1000, status: 'SUCCESS', page: 0, size: 50,
    }, { name: 'search' });
    searchLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'search ok': r => r.status === 200 });
  }
}

export function teardown(data) {
  teardownTestEntities(BASE_URL, data);
}

export function handleSummary(data) {
  return {
    'benchmark/k6/results/java-graphql-memory-stress-summary.json': JSON.stringify(data, null, 2),
  };
}

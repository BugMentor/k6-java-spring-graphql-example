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
const paymentCreateLatency = new Trend('payment_create_latency', true);

const BASE_URL = __ENV.BASE_URL || 'http://payment-service.payments.svc.cluster.local';
const GRAPHQL_URL = `${BASE_URL}/graphql`;
const TARGET_VUS = parseInt(__ENV.TARGET_VUS) || 500;

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
query($userId: String!, $limit: Int) {
  payments(userId: $userId, limit: $limit) {
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
    { duration: '30s', target: Math.floor(TARGET_VUS * 0.1) },
    { duration: '30s', target: Math.floor(TARGET_VUS * 0.3) },
    { duration: '1m', target: Math.floor(TARGET_VUS * 0.5) },
    { duration: '1m', target: Math.floor(TARGET_VUS * 0.7) },
    { duration: '2m', target: TARGET_VUS },
    { duration: '5m', target: TARGET_VUS },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<15000', 'p(99)<30000'],
    http_req_failed: ['rate<0.15'],
  },
  noConnectionReuse: false,
  batchPerHost: 20,
};

export function setup() {
  return setupTestEntities(BASE_URL, 'capacity');
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
    const amount = (Math.random() * 100 + 0.01).toFixed(2);
    const res = graphql(WALLET_TRANSFER_MUTATION, {
      walletId, merchantId, amount: parseFloat(amount),
    }, { name: 'wallet_transfer' });
    walletTransferLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'wallet transfer ok': r => r.status === 200 });

  } else if (scenario < 0.55) {
    const payments = [{
      userId, merchantId,
      amount: (Math.random() * 100 + 0.01).toFixed(2),
      type: 'DEBIT',
    }];
    const res = graphql(BATCH_MUTATION, { payments }, { name: 'batch' });
    batchLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'batch ok': r => r.status === 200 });

  } else if (scenario < 0.70) {
    const amount = (Math.random() * 50 + 0.01).toFixed(2);
    const res = graphql(WALLET_TRANSFER_MUTATION, {
      walletId, merchantId, amount: parseFloat(amount),
    }, { name: 'wallet_transfer_2' });
    walletTransferLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'wallet transfer 2 ok': r => r.status === 200 });

  } else if (scenario < 0.85) {
    const amount = (Math.random() * 1000 + 1).toFixed(2);
    const res = graphql(TOP_UP_MUTATION, {
      walletId, amount: parseFloat(amount),
    }, { name: 'top_up' });
    topUpLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'topup ok': r => r.status === 200 });

  } else {
    const res = graphql(USER_PAYMENTS_QUERY, {
      userId, limit: 5,
    }, { name: 'user_payments' });
    paymentCreateLatency.add(Date.now() - res.timings.duration);
    errorRate.add(res.status !== 200);
    check(res, { 'user payments ok': r => r.status === 200 });
  }
}

export function teardown(data) {
  teardownTestEntities(BASE_URL, data);
}

export function handleSummary(data) {
  return {
    'benchmark/k6/results/java-graphql-max-capacity-summary.json': JSON.stringify(data, null, 2),
  };
}

import { check, sleep } from 'k6';
import { Trend, Rate, Gauge } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const GRAPHQL_URL = `${BASE_URL}/graphql`;
const TARGET_VUS = parseInt(__ENV.TARGET_VUS || '50');

const errors = new Rate('errors');
const walletTransferLatency = new Trend('wallet_transfer_latency');
const paymentCreateLatency = new Trend('payment_create_latency');
const graphqlLatency = new Trend('graphql_latency');
const topUpLatency = new Trend('top_up_latency');
const searchLatency = new Trend('search_latency');
const restGetLatency = new Trend('rest_get_latency');
const concurrentVUs = new Gauge('concurrent_vus');

export const options = {
  stages: [
    { target: Math.floor(TARGET_VUS * 0.3), duration: '2m' },
    { target: Math.floor(TARGET_VUS * 0.6), duration: '3m' },
    { target: TARGET_VUS, duration: '5m' },
    { target: TARGET_VUS, duration: '60m' },
    { target: 0, duration: '2m' },
  ],
  thresholds: {
    http_req_duration: ['p(95)<3000', 'p(99)<5000'],
    http_req_failed: ['rate<0.05'],
    wallet_transfer_latency: ['p(95)<3000'],
    payment_create_latency: ['p(95)<2000'],
    graphql_latency: ['p(95)<4000'],
  },
};

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

function executeGraphQL(query, variables) {
  const payload = JSON.stringify({ query, variables });
  return http.post(GRAPHQL_URL, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
}

function refuelWalletIfNeeded(walletId, minBalance) {
  try {
    const getWalletQuery = `
      query($id: String!) {
        wallet(id: $id) {
          balance
        }
      }
    `;
    const r = executeGraphQL(getWalletQuery, { id: walletId });
    if (r.status === 200) {
      const balance = parseFloat(r.json().data.wallet.balance);
      if (balance < minBalance) {
        const topUpAmount = (500000 - balance).toFixed(2);
        const topUpQuery = `
          mutation($walletId: String!, $amount: Float!) {
            topUpWallet(walletId: $walletId, amount: $amount) {
              id
              balance
            }
          }
        `;
        const tr = executeGraphQL(topUpQuery, {
          walletId: walletId,
          amount: parseFloat(topUpAmount),
        });
        if (tr.status === 200) {
          console.log(`[REFUEL] Wallet ${walletId} topped up by ${topUpAmount}`);
        }
      }
    }
  } catch (e) { }
}

export function setup() {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(2, 8);
  const email = `loadtest_${timestamp}_${random}@k6.io`;
  const name = `K6 LoadTest ${Math.floor(Math.random() * 100000)}`;

  const createUserQuery = `
    mutation($email: String!, $fullName: String!, $status: String) {
      createUser(email: $email, fullName: $fullName, status: $status) { id }
    }
  `;
  const userRes = executeGraphQL(createUserQuery, { email, fullName: name, status: 'ACTIVE' });
  check(userRes, { 'user created': r => r.status === 200 }) ||
    (() => { throw new Error(`Setup failed: create user (${userRes.status})`); })();
  const userId = userRes.json().data.createUser.id;

  const createMerchantQuery = `
    mutation($name: String!, $apiKey: String!) {
      createMerchant(name: $name, apiKey: $apiKey) { id }
    }
  `;
  const merchantRes = executeGraphQL(createMerchantQuery, {
    name: `K6 Merchant ${timestamp}`,
    apiKey: `k6-${generateUUID()}`,
  });
  check(merchantRes, { 'merchant created': r => r.status === 200 }) ||
    (() => { throw new Error(`Setup failed: create merchant (${merchantRes.status})`); })();
  const merchantId = merchantRes.json().data.createMerchant.id;

  const createWalletQuery = `
    mutation($userId: String!, $balance: Float, $currency: String) {
      createWallet(userId: $userId, balance: $balance, currency: $currency) { id }
    }
  `;
  const walletRes = executeGraphQL(createWalletQuery, {
    userId, balance: 999999.99, currency: 'USD',
  });
  check(walletRes, { 'wallet created': r => r.status === 200 }) ||
    (() => { throw new Error(`Setup failed: create wallet (${walletRes.status})`); })();
  const walletId = walletRes.json().data.createWallet.id;

  const topUpQuery = `
    mutation($walletId: String!, $amount: Float!) {
      topUpWallet(walletId: $walletId, amount: $amount) { id balance }
    }
  `;
  const topUpRes = executeGraphQL(topUpQuery, { walletId, amount: 9999999.99 });
  check(topUpRes, { 'wallet funded': r => r.status === 200 });

  console.log(`SETUP: user=${userId} wallet=${walletId} merchant=${merchantId}`);
  return { userId, merchantId, walletId };
}

export default function (data) {
  concurrentVUs.add(1);

  const walletId = data.walletId;
  const merchantId = data.merchantId;
  const userId = data.userId;

  try {
    refuelWalletIfNeeded(walletId, 1000);

    const scenario = Math.random();

    if (scenario < 0.25) {
      const mutation = `
        mutation($walletId: String!, $merchantId: String!, $amount: Float!) {
          walletTransfer(walletId: $walletId, merchantId: $merchantId, amount: $amount) {
            id
            status
            amount
          }
        }
      `;
      const res = executeGraphQL(mutation, {
        walletId, merchantId,
        amount: Math.round(Math.random() * 100 * 100) / 100 + 0.01,
      });
      walletTransferLatency.add(res.timings.duration);
      check(res, { 'wallet transfer ok': r => r.status === 200 }) || errors.add(1);

    } else if (scenario < 0.40) {
      const mutation = `
        mutation($input: ProcessPaymentInput!) {
          processPayment(input: $input) { id status amount }
        }
      `;
      const res = executeGraphQL(mutation, {
        input: {
          userId, merchantId,
          amount: Math.round(Math.random() * 500 * 100) / 100 + 0.01,
          type: 'DEBIT',
        },
      });
      paymentCreateLatency.add(res.timings.duration);
      check(res, { 'payment create ok': r => r.status === 200 }) || errors.add(1);

    } else if (scenario < 0.55) {
      const query = `
        query($userId: String!, $limit: Int) {
          payments(userId: $userId, limit: $limit) { id amount status createdAt }
        }
      `;
      const res = executeGraphQL(query, { userId, limit: 5 });
      graphqlLatency.add(res.timings.duration);
      check(res, { 'graphql ok': r => r.status === 200 }) || errors.add(1);

    } else if (scenario < 0.65) {
      const mutation = `
        mutation($walletId: String!, $amount: Float!) {
          topUpWallet(walletId: $walletId, amount: $amount) { id balance }
        }
      `;
      const res = executeGraphQL(mutation, {
        walletId,
        amount: Math.round(Math.random() * 1000 * 100) / 100 + 1,
      });
      topUpLatency.add(res.timings.duration);
      check(res, { 'topup ok': r => r.status === 200 }) || errors.add(1);

    } else if (scenario < 0.75) {
      const query = `
        query($minAmount: Float, $maxAmount: Float, $status: String, $page: Int, $size: Int) {
          searchPayments(minAmount: $minAmount, maxAmount: $maxAmount, status: $status, page: $page, size: $size) {
            id amount status createdAt
          }
        }
      `;
      const res = executeGraphQL(query, { minAmount: 1, maxAmount: 500, status: 'SUCCESS', page: 0, size: 10 });
      searchLatency.add(res.timings.duration);
      check(res, { 'search ok': r => r.status === 200 }) || errors.add(1);

    } else if (scenario < 0.85) {
      const query = `
        query($userId: String!, $limit: Int) {
          payments(userId: $userId, limit: $limit) { id amount status createdAt }
        }
      `;
      const res = executeGraphQL(query, { userId, limit: 10 });
      restGetLatency.add(res.timings.duration);
      check(res, { 'get user payments ok': r => r.status === 200 }) || errors.add(1);

    } else if (scenario < 0.95) {
      const mutation = `
        mutation($input: ProcessPaymentInput!) {
          processPayment(input: $input) { id status }
        }
      `;
      const res = executeGraphQL(mutation, {
        input: {
          userId, merchantId,
          amount: Math.round(Math.random() * 200 * 100) / 100 + 0.01,
          type: 'DEBIT',
        },
      });
      graphqlLatency.add(res.timings.duration);
      check(res, { 'graphql process payment ok': r => r.status === 200 }) || errors.add(1);

    } else {
      const query = `
        query($startDate: String!, $endDate: String!) {
          paymentSummary(startDate: $startDate, endDate: $endDate) {
            totalsByStatus { status total }
          }
        }
      `;
      const res = executeGraphQL(query, {
        startDate: '2020-01-01T00:00:00Z',
        endDate: '2030-12-31T23:59:59Z',
      });
      check(res, { 'summary ok': r => r.status === 200 }) || errors.add(1);
    }
  } catch (e) {
    errors.add(1);
    console.error(`Error in VU iteration: ${e.message}`);
  }

  concurrentVUs.add(-1);
  sleep(0.1);
}

export function teardown(data) {
  if (!data) return;

  const deleteUserQuery = `mutation($id: String!) { deleteUser(id: $id) }`;
  const deleteMerchantQuery = `mutation($id: String!) { deleteMerchant(id: $id) }`;

  if (data.userId) executeGraphQL(deleteUserQuery, { id: data.userId });
  if (data.merchantId) executeGraphQL(deleteMerchantQuery, { id: data.merchantId });
}

export function handleSummary(data) {
  const box = `
============================================================
  ONE HOUR LOAD TEST - JAVA SPRING GRAPHQL (GraphQL Only)
============================================================
  Target VUs:         ${TARGET_VUS}
  Total Requests:     ${data.metrics.http_reqs?.values?.count || 0}
  Request Rate:       ${(data.metrics.http_reqs?.values?.rate || 0).toFixed(2)}/s
  P95 Duration:       ${(data.metrics.http_req_duration?.values?.['p(95)'] || 0).toFixed(2)}ms
  P99 Duration:       ${(data.metrics.http_req_duration?.values?.['p(99)'] || 0).toFixed(2)}ms
  Avg Duration:       ${(data.metrics.http_req_duration?.values?.avg || 0).toFixed(2)}ms
  Error Rate:         ${((data.metrics.errors?.values?.rate || 0) * 100).toFixed(2)}%
  Max Duration:       ${(data.metrics.http_req_duration?.values?.max || 0).toFixed(2)}ms
  Peak VUs:           ${data.metrics.vus_max?.values?.max || 0}
  Peak Iterations/s:  ${(data.metrics.iterations?.values?.rate || 0).toFixed(2)}
============================================================
`;
  console.log(box);

  return {
    'benchmark/k6/results/java-1h-summary.json': JSON.stringify(data, null, 2),
  };
}

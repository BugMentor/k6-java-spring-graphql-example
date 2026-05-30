import http from 'k6/http';
import { check } from 'k6';

const GRAPHQL_URL = `${__ENV.BASE_URL || 'http://localhost:8080'}/graphql`;

export function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0;
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
  });
}

function executeGraphQL(query, variables) {
  return http.post(GRAPHQL_URL, JSON.stringify({ query, variables }), {
    headers: { 'Content-Type': 'application/json' },
  });
}

export function setupTestEntities(baseUrl, prefix = 'loadtest') {
  const timestamp = Date.now();
  const random = Math.random().toString(36).substring(2, 8);
  const email = `${prefix}_${timestamp}_${random}@k6.io`;
  const name = `K6 ${prefix} ${Math.floor(Math.random() * 100000)}`;

  const createUserQuery = `
    mutation($email: String!, $fullName: String!, $status: String) {
      createUser(email: $email, fullName: $fullName, status: $status) {
        id
      }
    }
  `;
  const userRes = executeGraphQL(createUserQuery, {
    email, fullName: name, status: 'ACTIVE',
  });
  check(userRes, { 'user created via graphql': r => r.status === 200 }) ||
    (() => { throw new Error(`Setup failed: create user (${userRes.status})`); })();
  const userId = userRes.json().data.createUser.id;

  const createMerchantQuery = `
    mutation($name: String!, $apiKey: String!) {
      createMerchant(name: $name, apiKey: $apiKey) {
        id
      }
    }
  `;
  const merchantRes = executeGraphQL(createMerchantQuery, {
    name: `K6 ${prefix} Merchant ${timestamp}`,
    apiKey: `k6-${prefix}-${generateUUID()}`,
  });
  check(merchantRes, { 'merchant created via graphql': r => r.status === 200 }) ||
    (() => { throw new Error(`Setup failed: create merchant (${merchantRes.status})`); })();
  const merchantId = merchantRes.json().data.createMerchant.id;

  const createWalletQuery = `
    mutation($userId: ID!, $balance: Float!, $currency: String!) {
      createWallet(userId: $userId, balance: $balance, currency: $currency) {
        id
      }
    }
  `;
  const walletRes = executeGraphQL(createWalletQuery, {
    userId, balance: 999999.99, currency: 'USD',
  });
  check(walletRes, { 'wallet created via graphql': r => r.status === 200 }) ||
    (() => { throw new Error(`Setup failed: create wallet (${walletRes.status})`); })();
  const walletId = walletRes.json().data.createWallet.id;

  const topUpQuery = `
    mutation($walletId: ID!, $amount: Float!) {
      topUpWallet(walletId: $walletId, amount: $amount) {
        id
        balance
      }
    }
  `;
  const topUpRes = executeGraphQL(topUpQuery, {
    walletId, amount: 9999999.99,
  });
  check(topUpRes, { 'wallet funded via graphql': r => r.status === 200 });

  console.log(`SETUP: user=${userId} wallet=${walletId} merchant=${merchantId}`);
  return { userId, merchantId, walletId };
}

export function teardownTestEntities(baseUrl, data) {
  if (!data) return;

  const deleteUserQuery = `
    mutation($id: ID!) {
      deleteUser(id: $id)
    }
  `;
  const deleteMerchantQuery = `
    mutation($id: ID!) {
      deleteMerchant(id: $id)
    }
  `;

  if (data.userId) {
    executeGraphQL(deleteUserQuery, { id: data.userId });
  }
  if (data.merchantId) {
    executeGraphQL(deleteMerchantQuery, { id: data.merchantId });
  }
}

export function buildRampStages(targetVUs, scaleSteps, stepDuration, cooldownDuration) {
  const stages = [];
  const step = Math.ceil(targetVUs / scaleSteps);
  for (let i = 1; i <= scaleSteps; i++) {
    stages.push({ duration: stepDuration, target: step * i });
  }
  stages.push({ duration: cooldownDuration, target: 0 });
  return stages;
}

export function printScalingBox(title, metrics) {
  const {
    targetVUs = 0, maxVUs = 0, totalReqs = 0, failedReqs = 0,
    avgDuration = 0, p95 = 0, p99 = 0, note = ''
  } = metrics;

  return `
╔══════════════════════════════════════════════════════════╗
║  ${title.padEnd(52)}║
╠══════════════════════════════════════════════════════════╣
║  Target VUs:        ${String(targetVUs).padStart(8)}                       ║
║  Peak VUs:          ${String(maxVUs).padStart(8)}                       ║
║  Total Requests:    ${String(totalReqs).padStart(8)}                       ║
║  Failed Requests:   ${String(failedReqs).padStart(8)}                       ║
║  Avg Duration:      ${(avgDuration * 1000).toFixed(2).padStart(8)} ms                   ║
║  P95 Duration:      ${(p95 * 1000).toFixed(2).padStart(8)} ms                   ║
║  P99 Duration:      ${(p99 * 1000).toFixed(2).padStart(8)} ms                   ║
╠══════════════════════════════════════════════════════════╣
║  GRAFANA:           http://localhost:3002                ║
║  ${note.padEnd(52)}║
╚══════════════════════════════════════════════════════════╝
`;
}

export function refuelWalletIfNeeded(baseUrl, walletId, minBalance) {
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
          console.log(`[REFUEL] Wallet ${walletId} topped up by ${topUpAmount}, balance now ~500000`);
        }
      }
    }
  } catch (e) { }
}

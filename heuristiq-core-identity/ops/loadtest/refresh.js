// Token refresh load: a fixed arrival rate of POST /identity/auth/refresh.
//
// Every refresh ROTATES the token (the old row is revoked, a new refresh_tokens row and a session update are
// written), so a refresh chain cannot be shared. Each VU therefore owns one user: its first iteration signs in
// (tagged login_warmup, outside the refresh thresholds) and every later iteration refreshes with the token the
// previous refresh returned. The number of concurrent chains is the number of VUs the executor needs; keep
// MAX_VUS at or below the number of credentials so no two VUs share a user.
//
//   k6 run -e PASSWORD=... -e TARGET_RPS=200 refresh.js

import { cfg, requireConfig } from './lib/config.js';
import { loadCredentials } from './lib/data.js';
import { refresh, ensureSession, hasRefreshToken, EP } from './lib/client.js';
import { buildThresholds, plateauStages } from './lib/thresholds.js';
import { vuUser } from './lib/mix.js';
import { summaryOutputs } from './lib/summary.js';
import exec from 'k6/execution';

requireConfig();
const creds = loadCredentials(open(cfg.credentialsFile));

export const options = {
  scenarios: {
    refresh: {
      executor: 'ramping-arrival-rate',
      startRate: cfg.startRps,
      timeUnit: '1s',
      preAllocatedVUs: cfg.preAllocatedVus,
      maxVUs: cfg.maxVus,
      stages: plateauStages(),
      exec: 'refreshScenario',
    },
  },
  thresholds: buildThresholds([EP.refresh], false),
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function refreshScenario() {
  const own = vuUser(creds, exec.vu.idInTest);
  if (!hasRefreshToken()) {
    ensureSession(own); // warm-up login for this VU
    return;
  }
  refresh();
}

export function handleSummary(data) {
  return summaryOutputs('refresh', data);
}

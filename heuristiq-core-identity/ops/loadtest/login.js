// Login load: a fixed ARRIVAL RATE of full logins (logins per second), not a fixed number of VUs.
// Each iteration signs in as a random seeded user, so the username lookup, credential, membership, device,
// session and token writes touch rows all over the 10M-row tables (not one hot row).
//
//   k6 run -e PASSWORD=... -e TARGET_RPS=20 login.js
//
// Start low: one login costs a bcrypt verification (~hundreds of ms of CPU), so a 4-core instance sustains
// only on the order of ten logins per second. Raise TARGET_RPS in steps; use breakpoint.js to find the limit.

import { cfg, requireConfig } from './lib/config.js';
import { loadCredentials } from './lib/data.js';
import { login, EP } from './lib/client.js';
import { buildThresholds, plateauStages } from './lib/thresholds.js';
import { randomUser } from './lib/mix.js';
import { summaryOutputs } from './lib/summary.js';

requireConfig();
const creds = loadCredentials(open(cfg.credentialsFile));

export const options = {
  scenarios: {
    login: {
      executor: 'ramping-arrival-rate',
      startRate: cfg.startRps,
      timeUnit: '1s',
      preAllocatedVUs: cfg.preAllocatedVus,
      maxVUs: cfg.maxVus,
      stages: plateauStages(),
      exec: 'loginScenario',
    },
  },
  thresholds: buildThresholds([EP.login], false),
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function loginScenario() {
  login(randomUser(creds), EP.login);
}

export function handleSummary(data) {
  return summaryOutputs('login', data);
}

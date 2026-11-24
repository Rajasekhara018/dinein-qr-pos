// Soak: a long, steady mixed load to expose leaks and slow degradation that a 10-minute test cannot:
// heap and GC pressure, thread or connection leaks (Hikari pool, bcrypt queue), file descriptors, table and
// index bloat from the hot rows UAM updates on every login (user_login_security, user_devices, user_sessions,
// refresh_tokens), and the growth of tables that nothing purges (see docs/capacity/query-review.md).
//
// The rate is held constant (constant-arrival-rate). Choose SOAK_RPS around 50-70% of the rate that
// breakpoint.js found, so the soak measures stability, not saturation.
//
//   k6 run -e PASSWORD=... -e SOAK_RPS=100 -e SOAK_DURATION=4h soak.js
//
// What to compare: the first 15 minutes against the last 15 minutes of the same run, on the Grafana dashboard
// (p95, JVM heap after GC, Hikari active/pending, bcrypt queue, DB locks, table sizes) - the thresholds below
// only catch gross failure. Access tokens last 60 minutes, so a soak also exercises many expiries; VUs rotate
// their token ahead of expiry (TOKEN_SKEW_SECONDS).

import { cfg, requireConfig, num, str } from './lib/config.js';
import { loadCredentials } from './lib/data.js';
import { EP } from './lib/client.js';
import { buildThresholds } from './lib/thresholds.js';
import { loadMix, mixedIteration, vuUser } from './lib/mix.js';
import { summaryOutputs } from './lib/summary.js';
import exec from 'k6/execution';

requireConfig();
const creds = loadCredentials(open(cfg.credentialsFile));
const mix = loadMix(open(cfg.scenarioFile));

const soakRps = num('SOAK_RPS', 50);
const soakDuration = str('SOAK_DURATION', '2h');

export const options = {
  scenarios: {
    soak: {
      executor: 'constant-arrival-rate',
      rate: soakRps,
      timeUnit: '1s',
      duration: soakDuration,
      preAllocatedVUs: cfg.preAllocatedVus,
      maxVUs: cfg.maxVus,
      exec: 'soakScenario',
    },
  },
  thresholds: buildThresholds(
    [EP.login, EP.refresh, EP.logout, EP.mePrivileges, EP.meOrganizations, EP.userGet, EP.userByPhone]
      .concat(cfg.includeList ? [EP.userList] : [])
      .concat(cfg.includeSearch ? [EP.userSearch] : []),
    false),
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function soakScenario() {
  mixedIteration(mix, creds, vuUser(creds, exec.vu.idInTest));
}

export function handleSummary(data) {
  return summaryOutputs('soak (' + soakRps + ' rps for ' + soakDuration + ')', data);
}

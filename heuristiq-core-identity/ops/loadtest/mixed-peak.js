// Realistic peak: the whole request mix of the capacity model at one arrival rate.
//
// Weights come from ../../docs/capacity/scenario.json (loadtest.mix): logins, refreshes, privilege and
// organization fetches, sibling-service lookups, logouts and the staff list/search screens, in the proportions
// the model derives for its "expected" scenario. Override with -e MIX_JSON='{"login":20,"refresh":80}'.
//
// TARGET_RPS is the TOTAL request rate. Use it to replay a rate from the model (its total peak rps, divided by
// the number of instances you are testing) or the rate from breakpoint.js times a safety factor.
//
//   k6 run -e PASSWORD=... -e TARGET_RPS=150 mixed-peak.js
//
// Extra requests the mix does not contain: each VU signs in once at its first iteration (login_warmup) and
// re-signs in after a logout action; those are tagged separately and are not judged by the login thresholds.

import { cfg, requireConfig } from './lib/config.js';
import { loadCredentials } from './lib/data.js';
import { EP } from './lib/client.js';
import { buildThresholds, plateauStages } from './lib/thresholds.js';
import { loadMix, mixedIteration, vuUser } from './lib/mix.js';
import { summaryOutputs } from './lib/summary.js';
import exec from 'k6/execution';

requireConfig();
const creds = loadCredentials(open(cfg.credentialsFile));
const mix = loadMix(open(cfg.scenarioFile));

export const options = {
  scenarios: {
    mixed: {
      executor: 'ramping-arrival-rate',
      startRate: cfg.startRps,
      timeUnit: '1s',
      preAllocatedVUs: cfg.preAllocatedVus,
      maxVUs: cfg.maxVus,
      stages: plateauStages(),
      exec: 'mixedScenario',
    },
  },
  thresholds: buildThresholds(
    [EP.login, EP.refresh, EP.logout, EP.mePrivileges, EP.meOrganizations, EP.userGet, EP.userByPhone]
      .concat(cfg.includeList ? [EP.userList] : [])
      .concat(cfg.includeSearch ? [EP.userSearch] : []),
    false),
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function mixedScenario() {
  mixedIteration(mix, creds, vuUser(creds, exec.vu.idInTest));
}

export function handleSummary(data) {
  return summaryOutputs('mixed-peak', data);
}

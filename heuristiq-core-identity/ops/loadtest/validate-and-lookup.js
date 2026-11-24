// Authenticated read load: what a signed-in user and the sibling services ask of UAM once they hold a token.
//
// UAM has no "validate token" endpoint: the ApiGateway and UAM itself verify the JWT signature locally (HMAC),
// with no database call. "Validation" load on UAM is therefore every authenticated request's JWT filter, and
// the cheapest authenticated endpoints measure exactly that plus one small query:
//   GET /identity/me/privileges      (user + role + permission reads, no cache)
//   GET /identity/me/organizations   (memberships + tenants)
//   GET /users/{id}, /users/by-phone/{phone}   (sibling-service lookups)
//   GET /users?page=N                (paged list: OFFSET + count over 10M rows)
//   GET /users?search=term           (LIKE '%term%' over 7 columns: expected to be the slowest call)
// Weights are the read part of scenario.json loadtest.mix. Each VU signs in once (login_warmup) and keeps its token.
//
//   k6 run -e PASSWORD=... -e TARGET_RPS=300 validate-and-lookup.js
//   k6 run ... -e INCLUDE_SEARCH=false validate-and-lookup.js     # leave the heavy search out

import { cfg, requireConfig } from './lib/config.js';
import { loadCredentials } from './lib/data.js';
import { EP } from './lib/client.js';
import { buildThresholds, plateauStages } from './lib/thresholds.js';
import { loadMix, mixedIteration, vuUser } from './lib/mix.js';
import { summaryOutputs } from './lib/summary.js';
import exec from 'k6/execution';

requireConfig();
const creds = loadCredentials(open(cfg.credentialsFile));
const mix = loadMix(open(cfg.scenarioFile), ['me_privileges', 'me_organizations', 'user_lookup', 'user_list', 'user_search']);

export const options = {
  scenarios: {
    lookups: {
      executor: 'ramping-arrival-rate',
      startRate: cfg.startRps,
      timeUnit: '1s',
      preAllocatedVUs: cfg.preAllocatedVus,
      maxVUs: cfg.maxVus,
      stages: plateauStages(),
      exec: 'lookupScenario',
    },
  },
  thresholds: buildThresholds(
    [EP.mePrivileges, EP.meOrganizations, EP.userGet, EP.userByPhone]
      .concat(cfg.includeList ? [EP.userList] : [])
      .concat(cfg.includeSearch ? [EP.userSearch] : []),
    false),
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function lookupScenario() {
  mixedIteration(mix, creds, vuUser(creds, exec.vu.idInTest));
}

export function handleSummary(data) {
  return summaryOutputs('validate-and-lookup', data);
}

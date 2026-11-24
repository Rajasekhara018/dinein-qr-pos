// Breakpoint: raise the arrival rate in steps until a limit is breached, to find the rate ONE instance
// sustains. That number is what goes into scenario.json instance.sustainable_rps_per_instance
// (see results-template.md) - run it against a single UAM instance sized like production.
//
//   k6 run -e PASSWORD=... -e BP_MODE=mixed -e BP_START=20 -e BP_STEP=20 -e BP_STEPS=25 breakpoint.js
//
// BP_MODE: mixed (default, the scenario.json mix) | login | refresh | lookup (read endpoints only)
// Each step ramps for BP_RAMP then holds for BP_HOLD at its rate. Per step the run is judged on
// p95 latency (P95_MS, P95_LOGIN_MS for login mode) and on the failed-request rate (MAX_FAILED_RATE). The first
// breach aborts the run (abortOnFail); the highest step that passed is the sustainable OFFERED rate. Read the
// table printed at the end, then confirm on the Grafana dashboard WHICH resource saturated (CPU, DB pool wait,
// bcrypt queue, Postgres) - the table says where the limit is, not why.
//
// If k6 itself is the bottleneck (dropped_iterations rises while the server is idle) add load generators or
// raise MAX_VUS; a breakpoint measured against a saturated generator is meaningless.

import { cfg, requireConfig, num, str } from './lib/config.js';
import { loadCredentials } from './lib/data.js';
import { EP } from './lib/client.js';
import { buildThresholds, limitsFor } from './lib/thresholds.js';
import { loadMix, mixedIteration, vuUser, runAction } from './lib/mix.js';
import { summaryOutputs, metricValue, thresholdsOk } from './lib/summary.js';
import { refresh, ensureSession, hasRefreshToken } from './lib/client.js';
import exec from 'k6/execution';

requireConfig();
const creds = loadCredentials(open(cfg.credentialsFile));

const mode = str('BP_MODE', 'mixed');
const start = num('BP_START', 20);
const step = num('BP_STEP', 20);
const steps = num('BP_STEPS', 20);
const rampSeconds = num('BP_RAMP_SECONDS', 20);
const holdSeconds = num('BP_HOLD_SECONDS', 120);
const blockSeconds = rampSeconds + holdSeconds;

const readOnly = ['me_privileges', 'me_organizations', 'user_lookup', 'user_list', 'user_search'];
const mix = mode === 'lookup' ? loadMix(open(cfg.scenarioFile), readOnly)
  : mode === 'mixed' ? loadMix(open(cfg.scenarioFile)) : null;

if (['mixed', 'login', 'refresh', 'lookup'].indexOf(mode) < 0) {
  throw new Error('BP_MODE must be mixed, login, refresh or lookup, got "' + mode + '"');
}

const stepRates = [];
for (let i = 0; i < steps; i++) {
  stepRates.push(start + i * step);
}

const stages = [];
stepRates.forEach((rps) => {
  stages.push({ target: rps, duration: rampSeconds + 's' });
  stages.push({ target: rps, duration: holdSeconds + 's' });
});

const endpointTags = mode === 'login' ? [EP.login]
  : mode === 'refresh' ? [EP.refresh]
    : mode === 'lookup' ? [EP.mePrivileges, EP.meOrganizations, EP.userGet, EP.userByPhone]
      .concat(cfg.includeList ? [EP.userList] : []).concat(cfg.includeSearch ? [EP.userSearch] : [])
      : [EP.login, EP.refresh, EP.logout, EP.mePrivileges, EP.meOrganizations, EP.userGet, EP.userByPhone]
        .concat(cfg.includeList ? [EP.userList] : []).concat(cfg.includeSearch ? [EP.userSearch] : []);

// Global thresholds abort the run; per-endpoint ones only report. Per-step thresholds decide the verdict.
const thresholds = buildThresholds(endpointTags, true);
const stepLimits = limitsFor(mode === 'login' ? EP.login : EP.mePrivileges);
stepRates.forEach((rps) => {
  thresholds['http_req_duration{step:' + rps + '}'] = [
    { threshold: 'p(95)<' + stepLimits[0], abortOnFail: true, delayAbortEval: '45s' },
  ];
  thresholds['http_req_failed{step:' + rps + '}'] = [
    { threshold: 'rate<' + cfg.maxFailedRate, abortOnFail: true, delayAbortEval: '45s' },
  ];
  thresholds['http_reqs{step:' + rps + '}'] = ['count>=0']; // always true: makes the per-step count appear in the summary
});

export const options = {
  scenarios: {
    breakpoint: {
      executor: 'ramping-arrival-rate',
      startRate: cfg.startRps,
      timeUnit: '1s',
      preAllocatedVUs: cfg.preAllocatedVus,
      maxVUs: cfg.maxVus,
      stages: stages,
      exec: 'breakpointScenario',
    },
  },
  thresholds: thresholds,
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function currentStepRate() {
  const elapsed = (Date.now() - exec.scenario.startTime) / 1000;
  const index = Math.min(stepRates.length - 1, Math.floor(elapsed / blockSeconds));
  return stepRates[index];
}

export function breakpointScenario() {
  exec.vu.tags.step = String(currentStepRate());
  const own = vuUser(creds, exec.vu.idInTest);
  if (mode === 'login') {
    runAction('login', creds, own);
  } else if (mode === 'refresh') {
    if (!hasRefreshToken()) {
      ensureSession(own);
    } else {
      refresh();
    }
  } else {
    mixedIteration(mix, creds, own);
  }
}

export function handleSummary(data) {
  const rows = [];
  let lastPassing = null;
  let firstFailing = null;
  stepRates.forEach((rps) => {
    const dur = data.metrics['http_req_duration{step:' + rps + '}'];
    if (!dur) {
      return; // the run was aborted before this step
    }
    const count = metricValue(data, 'http_reqs{step:' + rps + '}', 'count');
    const failed = metricValue(data, 'http_req_failed{step:' + rps + '}', 'rate');
    const ok = thresholdsOk(dur) !== false && thresholdsOk(data.metrics['http_req_failed{step:' + rps + '}']) !== false;
    if (ok && firstFailing === null) {
      lastPassing = rps;
    }
    if (!ok && firstFailing === null) {
      firstFailing = rps;
    }
    rows.push('  ' + String(rps).padStart(10)
      + (count === null ? '-' : (count / blockSeconds).toFixed(1)).padStart(14)
      + (metricValue(data, 'http_req_duration{step:' + rps + '}', 'p(95)') || 0).toFixed(0).padStart(10)
      + (metricValue(data, 'http_req_duration{step:' + rps + '}', 'p(99)') || 0).toFixed(0).padStart(10)
      + ((failed || 0) * 100).toFixed(2).padStart(11)
      + '   ' + (ok ? 'pass' : 'FAIL'));
  });
  const table = '\nBreakpoint steps (mode ' + mode + ', ramp ' + rampSeconds + ' s + hold ' + holdSeconds + ' s each)\n'
    + '  offered rps  achieved rps    p95 ms    p99 ms   failed %   verdict\n' + rows.join('\n')
    + '\n\nHighest passing offered rate: ' + (lastPassing === null ? 'none (first step failed)' : lastPassing + ' rps')
    + '\nFirst failing step:           ' + (firstFailing === null ? 'none (limit not reached: raise BP_STEPS/BP_STEP)' : firstFailing + ' rps')
    + '\nRecord the passing rate as the rps at breakpoint in results-template.md, with the saturated resource.\n';
  return summaryOutputs('breakpoint (' + mode + ')', data, table);
}

// Spike: a sudden jump in the arrival rate and the recovery after it - the "push notification sends everyone
// to the app" or "shift change" case. Not a steady-state test: it asks
//   1. what happens during the jump (errors, 503 from the bcrypt queue, dropped iterations, pool wait), and
//   2. whether latency and errors return to the baseline after the spike (recovery), or stay degraded.
//
//   k6 run -e PASSWORD=... -e BASE_RPS=50 -e SPIKE_RPS=400 spike.js
//
// Shape: ramp to BASE_RPS, hold, jump to SPIKE_RPS within SPIKE_RAMP, hold the spike, drop back to BASE_RPS,
// hold the recovery period. The recovery stage is judged with its own, strict limits through the `phase` tag.
// Set -e SPIKE_MIX=login to spike logins only (a login storm), the default is the full mix.

import { cfg, requireConfig, num, str } from './lib/config.js';
import { loadCredentials } from './lib/data.js';
import { EP } from './lib/client.js';
import { limitsFor } from './lib/thresholds.js';
import { loadMix, mixedIteration, vuUser, runAction } from './lib/mix.js';
import { summaryOutputs } from './lib/summary.js';
import exec from 'k6/execution';

requireConfig();
const creds = loadCredentials(open(cfg.credentialsFile));
const mix = loadMix(open(cfg.scenarioFile));

const baseRps = num('BASE_RPS', 50);
const spikeRps = num('SPIKE_RPS', 400);
const warmup = str('SPIKE_WARMUP', '2m');
const baseHold = str('SPIKE_BASE_HOLD', '3m');
const spikeRamp = str('SPIKE_RAMP', '10s');
const spikeHold = str('SPIKE_HOLD', '3m');
const recovery = str('SPIKE_RECOVERY', '5m');
const spikeMix = str('SPIKE_MIX', 'mixed');

// Seconds from the start of the scenario at which the recovery phase begins, so requests can be tagged.
function seconds(text) {
  const m = /^(\d+)(s|m|h)$/.exec(text);
  if (!m) {
    throw new Error('Duration must look like 30s, 5m or 1h, got "' + text + '"');
  }
  return Number(m[1]) * (m[2] === 'h' ? 3600 : m[2] === 'm' ? 60 : 1);
}
const spikeStartsAt = seconds(warmup) + seconds(baseHold);
const spikeEndsAt = spikeStartsAt + seconds(spikeRamp) + seconds(spikeHold);
const recoveryStartsAt = spikeEndsAt + seconds(spikeRamp);

const [p95, p99] = limitsFor(EP.mePrivileges);

export const options = {
  scenarios: {
    spike: {
      executor: 'ramping-arrival-rate',
      startRate: cfg.startRps,
      timeUnit: '1s',
      preAllocatedVUs: cfg.preAllocatedVus,
      maxVUs: cfg.maxVus,
      stages: [
        { target: baseRps, duration: warmup },
        { target: baseRps, duration: baseHold },
        { target: spikeRps, duration: spikeRamp },
        { target: spikeRps, duration: spikeHold },
        { target: baseRps, duration: spikeRamp },
        { target: baseRps, duration: recovery },
      ],
      exec: 'spikeScenario',
    },
  },
  thresholds: {
    // During the spike a looser error budget is accepted; the point is what happens, so these are the gross limits.
    http_req_failed: ['rate<0.05'],
    // After the spike the system must be back to normal: strict limits on the recovery phase only.
    'http_req_failed{phase:recovery}': ['rate<' + cfg.maxFailedRate],
    'http_req_duration{phase:recovery}': ['p(95)<' + Math.max(p95, cfg.p95LoginMs), 'p(99)<' + Math.max(p99, cfg.p99LoginMs)],
    'http_req_failed{phase:baseline}': ['rate<' + cfg.maxFailedRate],
    // Not thresholded on purpose: the dropped-iterations count during the spike is a result to read, not a verdict.
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function currentPhase() {
  const elapsed = (Date.now() - exec.scenario.startTime) / 1000;
  if (elapsed < spikeStartsAt) {
    return 'baseline';
  }
  if (elapsed < recoveryStartsAt) {
    return 'spike';
  }
  return 'recovery';
}

export function spikeScenario() {
  exec.vu.tags.phase = currentPhase();
  if (spikeMix === 'login') {
    runAction('login', creds, vuUser(creds, exec.vu.idInTest));
    return;
  }
  mixedIteration(mix, creds, vuUser(creds, exec.vu.idInTest));
}

export function handleSummary(data) {
  const text = '\nPhases: baseline until ' + spikeStartsAt + ' s, spike until ' + recoveryStartsAt
    + ' s, recovery after. Compare Grafana p95/error panels across these three periods.\n';
  return summaryOutputs('spike (' + baseRps + ' -> ' + spikeRps + ' -> ' + baseRps + ' rps)', data, text);
}

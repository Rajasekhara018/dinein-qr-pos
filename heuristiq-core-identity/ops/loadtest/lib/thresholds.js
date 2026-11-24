// Threshold builders. Limits come from lib/config.js (environment variables); they are targets to hold the
// system to, not measurements. An `endpoint` tag filter keeps each endpoint judged on its own.

import { cfg } from './config.js';
import { EP } from './client.js';

/** Latency limits per endpoint tag: [p95, p99] in milliseconds. */
export function limitsFor(endpointTag) {
  switch (endpointTag) {
    case EP.login:
    case EP.loginWarmup:
    case EP.sendOtp:
      return [cfg.p95LoginMs, cfg.p99LoginMs];
    case EP.userList:
      return [cfg.p95ListMs, cfg.p99ListMs];
    case EP.userSearch:
      return [cfg.p95SearchMs, cfg.p99SearchMs];
    default:
      return [cfg.p95Ms, cfg.p99Ms];
  }
}

/**
 * Thresholds for a run: overall failure rate, dropped iterations (the generator or the server could not keep
 * the arrival rate), and p95/p99 for each endpoint that the script uses.
 * Pass abort=true (breakpoint) to stop the run at the first breach.
 */
export function buildThresholds(endpointTags, abort) {
  const wrap = (expr) => (abort ? { threshold: expr, abortOnFail: true, delayAbortEval: '30s' } : expr);
  const t = {
    http_req_failed: [wrap('rate<' + cfg.maxFailedRate)],
    dropped_iterations: ['count<=' + cfg.maxDropped],
    checks: ['rate>' + (1 - cfg.maxFailedRate)],
  };
  endpointTags.forEach((tag) => {
    const limits = limitsFor(tag);
    t['http_req_duration{endpoint:' + tag + '}'] = [
      wrap('p(95)<' + limits[0]),
      'p(99)<' + limits[1],
    ];
    t['http_req_failed{endpoint:' + tag + '}'] = ['rate<' + cfg.maxFailedRate];
  });
  return t;
}

/** Arrival-rate stages: ramp from startRps to targetRps, hold, ramp down. */
export function plateauStages() {
  return [
    { target: cfg.targetRps, duration: cfg.rampUp },
    { target: cfg.targetRps, duration: cfg.hold },
    { target: 0, duration: cfg.rampDown },
  ];
}

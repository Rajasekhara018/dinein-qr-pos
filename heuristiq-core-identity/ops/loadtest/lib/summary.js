// handleSummary helper: a short text report on stdout and, when SUMMARY_FILE is set (or via k6's own
// --summary-export), the full machine-readable summary as JSON for pasting into results-template.md.

import { cfg } from './config.js';

function metricValue(data, name, stat) {
  const m = data.metrics[name];
  if (!m || !m.values || m.values[stat] === undefined) {
    return null;
  }
  return m.values[stat];
}

function fmt(n, digits) {
  return n === null || n === undefined ? '-' : Number(n).toFixed(digits);
}

function thresholdsOk(metric) {
  if (!metric || !metric.thresholds) {
    return null;
  }
  const keys = Object.keys(metric.thresholds);
  if (keys.length === 0) {
    return null;
  }
  return keys.every((k) => {
    const entry = metric.thresholds[k];
    return typeof entry === 'object' ? entry.ok === true : entry === false;
  });
}

/** Names of the endpoint-tagged duration metrics present in the summary, e.g. http_req_duration{endpoint:login}. */
function endpointNames(data) {
  return Object.keys(data.metrics)
    .filter((k) => k.indexOf('http_req_duration{endpoint:') === 0)
    .map((k) => k.slice('http_req_duration{endpoint:'.length, -1))
    .sort();
}

export function textReport(title, data) {
  const lines = [];
  const durationMs = data.state && data.state.testRunDurationMs ? data.state.testRunDurationMs : null;
  lines.push('');
  lines.push('==== ' + title + ' ====');
  lines.push('target: ' + cfg.baseUrl + cfg.apiPrefix + '   auth: ' + cfg.authMode);
  if (durationMs) {
    lines.push('duration: ' + fmt(durationMs / 1000, 0) + ' s');
  }
  lines.push('requests: ' + fmt(metricValue(data, 'http_reqs', 'count'), 0)
    + '  (' + fmt(metricValue(data, 'http_reqs', 'rate'), 1) + '/s)'
    + '   failed: ' + fmt((metricValue(data, 'http_req_failed', 'rate') || 0) * 100, 2) + '%'
    + '   dropped iterations: ' + fmt(metricValue(data, 'dropped_iterations', 'count') || 0, 0));
  lines.push('');
  lines.push('endpoint               p50 ms    p95 ms    p99 ms    failed %   thresholds');
  endpointNames(data).forEach((name) => {
    const key = 'http_req_duration{endpoint:' + name + '}';
    const ok = thresholdsOk(data.metrics[key]);
    const failed = metricValue(data, 'http_req_failed{endpoint:' + name + '}', 'rate');
    lines.push(name.padEnd(22)
      + fmt(metricValue(data, key, 'med'), 0).padStart(8)
      + fmt(metricValue(data, key, 'p(95)'), 0).padStart(10)
      + fmt(metricValue(data, key, 'p(99)'), 0).padStart(10)
      + fmt(failed === null ? null : failed * 100, 2).padStart(11)
      + '   ' + (ok === null ? 'n/a' : ok ? 'pass' : 'FAIL'));
  });
  lines.push('');
  lines.push('Paste the table and the Grafana panels (uam-dashboard.json) into results-template.md.');
  lines.push('');
  return lines.join('\n');
}

/** Standard handleSummary result: text on stdout, JSON file when SUMMARY_FILE is set. */
export function summaryOutputs(title, data, extraText) {
  const outputs = { stdout: textReport(title, data) + (extraText || '') };
  if (cfg.summaryFile) {
    outputs[cfg.summaryFile] = JSON.stringify(data, null, 2);
  }
  return outputs;
}

export { metricValue, thresholdsOk };

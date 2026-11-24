// Central configuration for every UAM load-test script. Everything comes from environment variables so the
// same scripts run against a laptop stack, a staging stack and a pre-production stack without edits:
//
//   k6 run -e BASE_URL=http://localhost:9103 -e PASSWORD=... login.js
//
// Nothing secret lives in this repository: the test password is passed as PASSWORD at run time and the
// credentials file (usernames only) is produced by seed/export_credentials.sh and git-ignored.

function num(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') {
    return fallback;
  }
  const parsed = Number(raw);
  if (Number.isNaN(parsed)) {
    throw new Error('Environment variable ' + name + ' must be a number, got "' + raw + '"');
  }
  return parsed;
}

function str(name, fallback) {
  const raw = __ENV[name];
  return raw === undefined || raw === '' ? fallback : raw;
}

function flag(name, fallback) {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') {
    return fallback;
  }
  return raw === '1' || raw.toLowerCase() === 'true';
}

export const cfg = {
  // ---- target ------------------------------------------------------------------------------------------
  // UAM directly (default port 9103) isolates UAM. Pointing at the ApiGateway also exercises its rate limiter
  // (auth paths are limited per client), so for capacity work run from several source IPs or relax the limiter.
  baseUrl: str('BASE_URL', 'http://localhost:9103').replace(/\/+$/, ''),
  apiPrefix: str('API_PREFIX', '/api/v1/uam'),
  // Sent as X-Application-Code on login when set (UAM then checks the user may use that application).
  applicationCode: str('APPLICATION_CODE', ''),
  userAgent: str('USER_AGENT', 'k6-uam-loadtest/1.0'),

  // ---- authentication ----------------------------------------------------------------------------------
  // password: POST /identity/auth/login with username + PASSWORD.
  // otp:      POST /identity/auth/phone/send-otp then /phone/verify-otp. Needs UAM_OTP_EXPOSE_OTP=true on the
  //           target (the code is returned in the send response) and a reachable or stubbed NMS. UAM allows
  //           5 sends per phone per hour and one per 30 s, so use a large credentials file.
  authMode: str('AUTH_MODE', 'password'),
  password: str('PASSWORD', ''),
  userType: str('USER_TYPE', 'CUSTOMER'),
  // Where the credentials file produced by seed/export_credentials.sh lives (relative to the script that
  // runs, or absolute). Columns: user_id,username,phone
  credentialsFile: str('CREDENTIALS_FILE', './data/credentials.csv'),
  // Organization header sent on login/refresh. Only honoured for organization-wide accounts (organization switch).
  organizationCode: str('ORGANIZATION_CODE', ''),

  // ---- request volume knobs ----------------------------------------------------------------------------
  targetRps: num('TARGET_RPS', 20),            // plateau of the arrival rate (requests start per second)
  startRps: num('START_RPS', 1),
  rampUp: str('RAMP_UP', '2m'),
  hold: str('HOLD', '5m'),
  rampDown: str('RAMP_DOWN', '30s'),
  preAllocatedVus: num('PRE_VUS', 100),
  maxVus: num('MAX_VUS', 2000),
  // How many distinct pages deep GET /users is read (OFFSET paging cost grows with this) and the page size (UAM caps at 100).
  listMaxPage: num('LIST_MAX_PAGE', 50),
  listPageSize: num('LIST_PAGE_SIZE', 50),
  // Free-text search is a LIKE '%term%' scan over 7 columns: off unless asked for.
  includeList: flag('INCLUDE_LIST', true),
  includeSearch: flag('INCLUDE_SEARCH', true),
  // Refresh a session's access token when fewer than this many seconds remain (long soaks outlive the 60 min JWT).
  tokenSkewSeconds: num('TOKEN_SKEW_SECONDS', 120),

  // ---- pass/fail limits (service-level targets, parameters, NOT measurements) ---------------------------
  maxFailedRate: num('MAX_FAILED_RATE', 0.01),
  maxDropped: num('MAX_DROPPED', 50),
  p95Ms: num('P95_MS', 500),
  p99Ms: num('P99_MS', 1000),
  p95LoginMs: num('P95_LOGIN_MS', 1500),
  p99LoginMs: num('P99_LOGIN_MS', 2500),
  p95ListMs: num('P95_LIST_MS', 2000),
  p99ListMs: num('P99_LIST_MS', 4000),
  p95SearchMs: num('P95_SEARCH_MS', 5000),
  p99SearchMs: num('P99_SEARCH_MS', 8000),

  // ---- mix and scenario file --------------------------------------------------------------------------
  // Relative to the script. The default is the model's scenario file so test and model share one mix.
  scenarioFile: str('SCENARIO_FILE', '../../docs/capacity/scenario.json'),
  // Optional JSON override of the mix weights, e.g. -e MIX_JSON='{"login":50,"refresh":50}'
  mixJson: str('MIX_JSON', ''),

  // ---- output -----------------------------------------------------------------------------------------
  // Written by handleSummary in addition to stdout. Empty = no file.
  summaryFile: str('SUMMARY_FILE', ''),
};

export { num, str, flag };

/** Full URL for a path below the UAM API prefix, e.g. url('/identity/auth/login'). */
export function url(path) {
  return cfg.baseUrl + cfg.apiPrefix + path;
}

/** Stops the run before it starts when a required setting is missing. Call from every script's init code. */
export function requireConfig() {
  if (cfg.authMode === 'password' && !cfg.password) {
    throw new Error('PASSWORD is not set. Pass the password of the seeded test users: k6 run -e PASSWORD=... <script>');
  }
  if (cfg.authMode !== 'password' && cfg.authMode !== 'otp') {
    throw new Error('AUTH_MODE must be "password" or "otp", got "' + cfg.authMode + '"');
  }
}

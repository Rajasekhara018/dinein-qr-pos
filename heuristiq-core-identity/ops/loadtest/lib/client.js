// UAM API calls used by the scenarios, with one `endpoint` tag per call so results split per endpoint
// (thresholds, Prometheus labels, the summary). A VU keeps its own session state in module variables
// (every VU has its own JavaScript runtime), so a refresh-token chain is never shared between VUs.

import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';
import { cfg, url } from './config.js';

// Endpoint tag values. Thresholds and the dashboard refer to these names.
export const EP = {
  login: 'login',
  loginWarmup: 'login_warmup',     // first login of a VU, or a re-login after logout/expiry (not part of the mix)
  sendOtp: 'otp_send',
  refresh: 'refresh',
  logout: 'logout',
  mePrivileges: 'me_privileges',
  meOrganizations: 'me_organizations',
  userGet: 'user_get',
  userByPhone: 'user_by_phone',
  userList: 'user_list',
  userSearch: 'user_search',
};

// Per-VU session. Reset by logout() and by a failed refresh.
const session = { access: null, refresh: null, expiresAtMs: 0, user: null };

function jsonHeaders(extra) {
  const headers = { 'Content-Type': 'application/json', 'User-Agent': cfg.userAgent };
  if (cfg.applicationCode) {
    headers['X-Application-Code'] = cfg.applicationCode;
  }
  if (cfg.organizationCode) {
    headers['X-Organization-Code'] = cfg.organizationCode;
  }
  return Object.assign(headers, extra || {});
}

function authHeaders(token) {
  return { Authorization: 'Bearer ' + token, 'User-Agent': cfg.userAgent };
}

function body(requestObject) {
  // Every UAM write endpoint takes the CommonRequestObject envelope: the real payload is requestObject.
  return JSON.stringify({ requestObject: requestObject });
}

function tokensFrom(res) {
  try {
    const payload = res.json('responseObject');
    if (payload && (payload.accessToken || payload.token)) {
      return { access: payload.accessToken || payload.token, refresh: payload.refreshToken || null };
    }
  } catch (e) {
    // fall through: the check below records the failure
  }
  return null;
}

function expiryMs(jwt) {
  try {
    const part = jwt.split('.')[1];
    const claims = JSON.parse(encoding.b64decode(part, 'rawurl', 's'));
    return claims.exp * 1000;
  } catch (e) {
    return Date.now() + 5 * 60 * 1000;
  }
}

function adopt(tokens, user) {
  session.access = tokens.access;
  session.refresh = tokens.refresh;
  session.expiresAtMs = expiryMs(tokens.access);
  session.user = user;
}

/** Full login of `user` ({username, phone}). Returns true on success and stores the session on this VU. */
export function login(user, endpointTag) {
  const tag = endpointTag || EP.login;
  if (cfg.authMode === 'otp') {
    return loginWithOtp(user, tag);
  }
  const res = http.post(
    url('/identity/auth/login'),
    body({ username: user.username, password: cfg.password }),
    { headers: jsonHeaders(), tags: { endpoint: tag, name: 'POST /identity/auth/login' } },
  );
  const tokens = res.status === 200 ? tokensFrom(res) : null;
  const ok = check(res, { 'login 200 with tokens': () => tokens !== null }, { endpoint: tag });
  if (ok) {
    adopt(tokens, user);
  }
  return ok;
}

function loginWithOtp(user, tag) {
  const sent = http.post(
    url('/identity/auth/phone/send-otp'),
    body({ phone: user.phone, userType: cfg.userType }),
    { headers: jsonHeaders(), tags: { endpoint: EP.sendOtp, name: 'POST /identity/auth/phone/send-otp' } },
  );
  let otp = null;
  try {
    otp = sent.status === 200 ? sent.json('responseObject.otp') : null;
  } catch (e) {
    otp = null;
  }
  if (!check(sent, { 'otp sent and exposed (UAM_OTP_EXPOSE_OTP=true)': () => !!otp }, { endpoint: EP.sendOtp })) {
    return false;
  }
  const res = http.post(
    url('/identity/auth/phone/verify-otp'),
    body({ phone: user.phone, otp: otp, userType: cfg.userType }),
    { headers: jsonHeaders(), tags: { endpoint: tag, name: 'POST /identity/auth/phone/verify-otp' } },
  );
  const tokens = res.status === 200 ? tokensFrom(res) : null;
  const ok = check(res, { 'otp login 200 with tokens': () => tokens !== null }, { endpoint: tag });
  if (ok) {
    adopt(tokens, user);
  }
  return ok;
}

/** Rotates this VU's refresh token. Returns true on success; on failure the session is dropped. */
export function refresh() {
  if (!session.refresh) {
    return false;
  }
  const res = http.post(
    url('/identity/auth/refresh'),
    body({ refreshToken: session.refresh }),
    { headers: jsonHeaders(), tags: { endpoint: EP.refresh, name: 'POST /identity/auth/refresh' } },
  );
  const tokens = res.status === 200 ? tokensFrom(res) : null;
  const ok = check(res, { 'refresh 200 with tokens': () => tokens !== null }, { endpoint: EP.refresh });
  if (ok) {
    adopt(tokens, session.user);
  } else {
    session.access = null;
    session.refresh = null;
  }
  return ok;
}

/** Revokes the refresh tokens of the signed-in user. The access token stays valid (JWTs are stateless). */
export function logout() {
  if (!session.access) {
    return false;
  }
  const res = http.post(
    url('/identity/auth/logout'),
    null,
    { headers: Object.assign(authHeaders(session.access), { 'Content-Type': 'application/json' }),
      tags: { endpoint: EP.logout, name: 'POST /identity/auth/logout' } },
  );
  const ok = check(res, { 'logout 200': (r) => r.status === 200 }, { endpoint: EP.logout });
  session.refresh = null; // the server revoked it; the next refresh action signs in again
  return ok;
}

/**
 * Makes sure this VU holds a usable access token. First use (or after logout) signs in as `user` and tags it
 * login_warmup so it does not count towards the login endpoint's thresholds; a nearly expired token is rotated.
 */
export function ensureSession(user) {
  if (!session.access) {
    return login(user, EP.loginWarmup);
  }
  if (session.expiresAtMs - Date.now() < cfg.tokenSkewSeconds * 1000) {
    if (session.refresh && refresh()) {
      return true;
    }
    return login(user, EP.loginWarmup);
  }
  return true;
}

export function hasSession() {
  return session.access !== null;
}

export function hasRefreshToken() {
  return session.refresh !== null;
}

export function currentUser() {
  return session.user;
}

function authedGet(path, endpointTag, name) {
  const res = http.get(url(path), {
    headers: authHeaders(session.access),
    tags: { endpoint: endpointTag, name: name },
  });
  check(res, { [endpointTag + ' 200']: (r) => r.status === 200 }, { endpoint: endpointTag });
  return res;
}

export function mePrivileges() {
  return authedGet('/identity/me/privileges', EP.mePrivileges, 'GET /identity/me/privileges');
}

export function meOrganizations() {
  return authedGet('/identity/me/organizations', EP.meOrganizations, 'GET /identity/me/organizations');
}

export function userGet(userId) {
  return authedGet('/users/' + encodeURIComponent(userId), EP.userGet, 'GET /users/{id}');
}

export function userByPhone(phone) {
  return authedGet('/users/by-phone/' + encodeURIComponent(phone), EP.userByPhone, 'GET /users/by-phone/{phone}');
}

export function userList(page, size) {
  return authedGet('/users?page=' + page + '&size=' + size, EP.userList, 'GET /users?page');
}

export function userSearch(term) {
  return authedGet('/users?search=' + encodeURIComponent(term) + '&size=20', EP.userSearch, 'GET /users?search');
}

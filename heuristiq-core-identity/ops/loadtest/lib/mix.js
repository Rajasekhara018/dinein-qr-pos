// The request mix: which UAM action an iteration performs. Weights come from the capacity model's
// scenario.json ("loadtest.mix"), so the test and the model describe the same traffic.

import { cfg } from './config.js';
import {
  login, refresh, logout, ensureSession, hasRefreshToken, EP,
  mePrivileges, meOrganizations, userGet, userByPhone, userList, userSearch,
} from './client.js';

/** Weights: MIX_JSON if given, else scenario.json loadtest.mix (text read by the script with open()). */
export function loadMix(scenarioText, onlyActions) {
  let weights;
  if (cfg.mixJson) {
    weights = JSON.parse(cfg.mixJson);
  } else {
    weights = JSON.parse(scenarioText).loadtest.mix;
  }
  if (onlyActions) {
    Object.keys(weights).forEach((k) => {
      if (onlyActions.indexOf(k) < 0) {
        delete weights[k];
      }
    });
  }
  if (!cfg.includeList) {
    delete weights.user_list;
  }
  if (!cfg.includeSearch) {
    delete weights.user_search;
  }
  const entries = Object.keys(weights).map((k) => [k, Number(weights[k])]).filter((e) => e[1] > 0);
  if (entries.length === 0) {
    throw new Error('The request mix is empty');
  }
  const total = entries.reduce((sum, e) => sum + e[1], 0);
  let acc = 0;
  return entries.map((e) => {
    acc += e[1] / total;
    return { action: e[0], upTo: acc };
  });
}

function pick(cumulative) {
  const r = Math.random();
  for (let i = 0; i < cumulative.length; i++) {
    if (r <= cumulative[i].upTo) {
      return cumulative[i].action;
    }
  }
  return cumulative[cumulative.length - 1].action;
}

export function randomUser(creds) {
  return creds[Math.floor(Math.random() * creds.length)];
}

/**
 * A VU keeps one identity so its refresh-token chain stays valid. The identity is spread over the credentials
 * by VU id; the `login` action signs in as a random user instead (new session, new refresh chain).
 */
export function vuUser(creds, vuIdInTest) {
  return creds[(vuIdInTest - 1) % creds.length];
}

/** Runs one action of the mix for the VU whose identity is `own`. */
export function runAction(action, creds, own) {
  switch (action) {
    case 'login':
      // A fresh full login as a random user: exercises the username/credential/membership lookups across all rows.
      login(randomUser(creds), EP.login);
      return;
    case 'refresh':
      if (!hasRefreshToken()) {
        // Nothing to rotate yet (first iteration, or after a logout): sign in, counted as warm-up not as refresh.
        ensureSession(own);
        return;
      }
      refresh();
      return;
    case 'logout':
      if (ensureSession(own)) {
        logout();
      }
      return;
    default:
      break;
  }
  if (!ensureSession(own)) {
    return;
  }
  switch (action) {
    case 'me_privileges':
      mePrivileges();
      break;
    case 'me_organizations':
      meOrganizations();
      break;
    case 'user_lookup':
      // Half by id, half by phone, like the sibling services that look users up.
      if (Math.random() < 0.5) {
        userGet(randomUser(creds).userId);
      } else {
        userByPhone(randomUser(creds).phone);
      }
      break;
    case 'user_list':
      userList(Math.floor(Math.random() * cfg.listMaxPage), cfg.listPageSize);
      break;
    case 'user_search':
      userSearch(randomUser(creds).username);
      break;
    default:
      throw new Error('Unknown action in mix: ' + action);
  }
}

/** One mixed iteration. `cumulative` comes from loadMix(). */
export function mixedIteration(cumulative, creds, own) {
  runAction(pick(cumulative), creds, own);
}

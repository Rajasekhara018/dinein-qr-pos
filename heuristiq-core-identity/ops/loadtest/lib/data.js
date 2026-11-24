// Loads the credentials file once and shares it between all VUs (SharedArray keeps one copy in memory).
//
// The text is read by the SCRIPT with open() and passed in, because open() resolves relative paths against the
// main script and must run in the init context. Expected header: user_id,username,phone

import { SharedArray } from 'k6/data';

/** Parses the CSV text produced by seed/export_credentials.sh into [{userId, username, phone}]. */
export function parseCredentials(text) {
  const lines = text.split(/\r?\n/).filter((line) => line.trim().length > 0);
  if (lines.length < 2) {
    throw new Error('Credentials file has no data rows. Run seed/export_credentials.sh first.');
  }
  const header = lines[0].trim().split(',');
  const idx = {
    userId: header.indexOf('user_id'),
    username: header.indexOf('username'),
    phone: header.indexOf('phone'),
  };
  if (idx.userId < 0 || idx.username < 0) {
    throw new Error('Credentials file must have the header user_id,username,phone (got: ' + lines[0] + ')');
  }
  const rows = [];
  for (let i = 1; i < lines.length; i++) {
    const cols = lines[i].trim().split(',');
    rows.push({
      userId: cols[idx.userId],
      username: cols[idx.username],
      phone: idx.phone >= 0 ? cols[idx.phone] : '',
    });
  }
  return rows;
}

/** Call at the top level of a script: const creds = loadCredentials(open(cfg.credentialsFile)); */
export function loadCredentials(text) {
  return new SharedArray('credentials', () => parseCredentials(text));
}

// Copies the freshly packed hq-shared-lib into every app that consumes it and keeps each app's package-lock.json in step.
//
// Why: every consuming app installs the library from a packed file (`file:hq-shared-lib-0.0.1.tgz`), and the lock file holds
// the sha512 of that file. A stale hash makes npm silently reuse an older cached copy, and a library that needs a package the
// app does not list (its peer dependencies, e.g. libphonenumber-js) makes the app's Docker/federation build fail. Doing this by
// hand per app caused both failures, so build.sh runs this script after packing.
//
// Usage (from heuristiq-web-common): node scripts/sync-consumers.mjs
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const LIB_DIR = join(ROOT, 'heuristiq-web-common');
const TGZ_NAME = 'hq-shared-lib-0.0.1.tgz';
const TGZ = join(LIB_DIR, 'dist', 'hq-shared-lib', TGZ_NAME);

/** The apps that depend on hq-shared-lib (folders next to heuristiq-web-common). Add a new consumer here. */
const CONSUMERS = ['heuristiq-web-shell', 'heuristiq-web-ride', 'heuristiq-web-wallet', 'heuristiq-web-report', 'heuristiq-web-accounting', 'heuristiq-web-matrimony-customer', 'heuristiq-web-matrimony-core', 'heuristiq-web-inventory', 'heuristiq-web-food'];

/** Peer dependencies of the library that every consumer must list itself (npm does not install them for a file: package). */
const libraryManifest = JSON.parse(readFileSync(join(LIB_DIR, 'projects', 'hq-shared-lib', 'package.json'), 'utf8'));
const REQUIRED_PEERS = Object.keys(libraryManifest.peerDependencies ?? {}).filter((name) => !name.startsWith('@angular/') && name !== 'rxjs');

if (!existsSync(TGZ)) {
  console.error(`Packed library not found: ${TGZ}. Run the build first.`);
  process.exit(1);
}

const integrity = 'sha512-' + createHash('sha512').update(readFileSync(TGZ)).digest('base64');
const problems = [];

for (const app of CONSUMERS) {
  const dir = join(ROOT, app);
  if (!existsSync(join(dir, 'package.json'))) {
    console.log(`skip ${app}: not found`);
    continue;
  }
  copyFileSync(TGZ, join(dir, TGZ_NAME));

  const manifest = JSON.parse(readFileSync(join(dir, 'package.json'), 'utf8'));
  const declared = { ...manifest.dependencies, ...manifest.devDependencies };
  for (const peer of REQUIRED_PEERS) {
    if (!declared[peer]) {
      problems.push(`${app}: package.json does not list "${peer}", a peer dependency of hq-shared-lib (add it, then npm install)`);
    }
  }

  const lockPath = join(dir, 'package-lock.json');
  if (existsSync(lockPath)) {
    const lock = readFileSync(lockPath, 'utf8');
    const pattern = /("node_modules\/hq-shared-lib": \{[^}]*?"integrity": ")([^"]+)(")/s;
    if (!pattern.test(lock)) {
      problems.push(`${app}: package-lock.json has no integrity entry for node_modules/hq-shared-lib (run npm install)`);
    } else {
      writeFileSync(lockPath, lock.replace(pattern, `$1${integrity}$3`));
    }
  }
  console.log(`synced ${app}`);
}

console.log(`integrity ${integrity}`);
if (problems.length) {
  console.error('\nProblems:\n - ' + problems.join('\n - '));
  process.exit(1);
}
console.log('Run `npm install --legacy-peer-deps` in each app so node_modules picks up the new package.');

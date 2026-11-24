// Runs the unit tests with coverage. Coverage merging needs far more memory than the tests themselves; without a
// larger heap the worker dies with "Worker exited unexpectedly" right after the tests finish. The heap size is set
// here (for the test workers too, which inherit the environment) so nobody has to export it by hand.
// Usage: npm run test:coverage [-- extra ng test arguments, e.g. --include "src/app/layout/**/*.spec.ts"]
import { spawnSync } from 'node:child_process';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const ng = require.resolve('@angular/cli/bin/ng.js');
const heapMb = process.env.COVERAGE_HEAP_MB ?? '3072';
const env = { ...process.env };
if (!/--max-old-space-size/.test(env.NODE_OPTIONS ?? '')) {
  env.NODE_OPTIONS = `${env.NODE_OPTIONS ?? ''} --max-old-space-size=${heapMb}`.trim();
}
if (!env.VITEST_MAX_WORKERS) env.VITEST_MAX_WORKERS = process.env.COVERAGE_WORKERS ?? '2';
const args = [ng, 'test', '--watch=false', '--coverage', '--coverage-reporters=html', '--coverage-reporters=text-summary', '--coverage-reporters=json-summary', ...process.argv.slice(2)];
const result = spawnSync(process.execPath, args, { stdio: 'inherit', env });
process.exit(result.status ?? 1);

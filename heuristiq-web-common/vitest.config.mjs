// Vitest settings merged into `ng test` (angular.json -> test.options.runnerConfig).
// With coverage on, the first test of a file also pays for instrumentation and a cold TestBed, which on a loaded
// machine or a CI agent regularly passes the 5 s default; a longer limit only prevents false "timed out" failures.
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    testTimeout: 30_000,
    hookTimeout: 30_000,
  },
});

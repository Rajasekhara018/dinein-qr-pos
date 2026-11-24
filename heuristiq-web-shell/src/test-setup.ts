import { vi } from 'vitest';

// The shared CI host runs the suites slowly (few workers, coverage instrumentation), so a heavy component test that takes a second
// locally can pass the default 5 s on Jenkins. A longer ceiling stops those false failures; a test that really hangs still fails.
vi.setConfig({ testTimeout: 30_000, hookTimeout: 30_000 });

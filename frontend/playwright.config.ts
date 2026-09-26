import { defineConfig, devices } from '@playwright/test';

const PORT = Number(process.env['E2E_PORT'] ?? 4200);
const BASE_URL = process.env['E2E_BASE_URL'] ?? `http://localhost:${PORT}`;

/**
 * E2E tests mock the backend with `page.route` / `page.routeWebSocket`, so only the Angular dev server is needed.
 * An already running `ng serve` on the same port is reused.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 2 : 0,
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: BASE_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'en-IN',
    timezoneId: 'Asia/Kolkata',
  },
  projects: [
    { name: 'mobile-chrome', use: { ...devices['Pixel 7'] } },
    {
      name: 'desktop-chrome',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 900 } },
    },
  ],
  webServer: process.env['E2E_BASE_URL']
    ? undefined
    : {
        command: `npx ng serve --port ${PORT}`,
        url: BASE_URL,
        reuseExistingServer: !process.env['CI'],
        timeout: 180_000,
        stdout: 'ignore',
        stderr: 'pipe',
      },
});

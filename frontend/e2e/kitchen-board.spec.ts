import { expect, Page, test } from '@playwright/test';
import { deviceSession, mockKitchenBackend, rememberDevice } from './fixtures/kitchen-backend';

type Column = 'CONFIRMED' | 'PREPARING' | 'READY';

/** Below 768px the columns are tabs; open the right one first. */
async function column(page: Page, isMobile: boolean, col: Column) {
  if (isMobile) await page.getByTestId(`tab-${col}`).click();
  return page.getByTestId(`column-${col}`);
}

test.describe('kitchen board', () => {
  test('PIN login → orders in the right columns → Start moves a ticket to Preparing', async ({
    page,
    isMobile,
  }) => {
    const mock = await mockKitchenBackend(page);

    await page.goto('/kitchen');
    await expect(page).toHaveURL(/\/kitchen\/login$/);
    // Dark theme by default.
    await expect(page.locator('html')).toHaveClass(/dark/);

    await page.getByLabel('Username').fill('cook');
    await page.getByLabel('PIN (4–6 digits)').fill('0000');
    await page.getByRole('button', { name: 'Sign in this screen' }).click();
    await expect(page.getByTestId('kitchen-login-error')).toContainText('Wrong username or PIN');

    await page.getByLabel('PIN (4–6 digits)').fill('1234');
    await page.getByRole('button', { name: 'Sign in this screen' }).click();
    await expect(page).toHaveURL(/\/kitchen$/);
    expect(mock.loginRequests.at(-1)).toMatchObject({
      username: 'cook',
      pin: '1234',
      deviceName: 'Kitchen screen',
    });

    await expect(page.getByRole('heading', { name: 'Spice Route' })).toBeVisible();
    await expect(page.getByTestId('connection-status')).toHaveText(/Live/);
    expect(mock.connectHeaders.some((f) => f.includes('Authorization:Bearer dvc_test_token'))).toBe(
      true,
    );

    // Oldest first in New; the 25-minute-old ticket is red with a text cue.
    const newCol = await column(page, isMobile, 'CONFIRMED');
    await expect(newCol.getByTestId('ticket-token')).toHaveText([/#101/, /#102/]);
    await expect(newCol.getByTestId('ticket-1')).toHaveAttribute('data-level', 'alert');
    await expect(newCol.getByTestId('ticket-1')).toContainText('Overdue');
    await expect(newCol.getByTestId('ticket-2')).toHaveAttribute('data-level', 'warn');
    await expect(newCol.getByTestId('ticket-1')).toContainText('No onion');

    await expect((await column(page, isMobile, 'PREPARING')).getByTestId('ticket-3')).toBeVisible();
    await expect((await column(page, isMobile, 'READY')).getByTestId('ticket-4')).toBeVisible();

    // Start → Preparing.
    await (
      await column(page, isMobile, 'CONFIRMED')
    )
      .getByRole('button', { name: 'Start token 101' })
      .click();
    await expect.poll(() => mock.statusRequests.length).toBe(1);
    expect(mock.statusRequests[0]).toMatchObject({
      id: 1,
      body: { status: 'PREPARING' },
      auth: 'Bearer dvc_test_token',
    });
    const prepCol = await column(page, isMobile, 'PREPARING');
    await expect(prepCol.getByTestId('ticket-1')).toBeVisible();
    await expect(prepCol.getByRole('button', { name: 'Ready token 101' })).toBeVisible();
    await expect((await column(page, isMobile, 'CONFIRMED')).getByTestId('ticket-1')).toHaveCount(
      0,
    );

    // Realtime: a new order pushed over STOMP appears in New immediately.
    const pushed = {
      id: 9,
      orderNumber: '260926-009',
      displayToken: 109,
      status: 'CONFIRMED',
      tableLabel: 'T9',
      items: [{ name: 'Masala Dosa', foodType: 'VEG', addons: [], quantity: 1 }],
      paidAt: new Date().toISOString(),
    };
    mock.orders.push(pushed);
    mock.push({
      type: 'ORDER_CONFIRMED',
      orderId: 9,
      status: 'CONFIRMED',
      order: pushed,
      at: pushed.paidAt,
    });
    const newAgain = await column(page, isMobile, 'CONFIRMED');
    await expect(newAgain.getByTestId('ticket-9')).toBeVisible();
    await expect(newAgain.getByTestId('ticket-9')).toContainText('New');
  });

  test('a remembered device skips the login; sign-out forgets it', async ({ page }) => {
    await mockKitchenBackend(page);
    await page.addInitScript(rememberDevice, deviceSession);
    await page.goto('/kitchen/login');
    await expect(page).toHaveURL(/\/kitchen$/);
    await expect(page.getByText('Pass screen')).toBeVisible();

    page.once('dialog', (dialog) => void dialog.accept());
    await page.getByRole('button', { name: 'Sign out this screen' }).click();
    await expect(page).toHaveURL(/\/kitchen\/login$/);
    expect(await page.evaluate(() => localStorage.getItem('dinein.kitchen.device.v1'))).toBeNull();
  });

  test('shows the Enable sound prompt until chosen', async ({ page }) => {
    await mockKitchenBackend(page);
    await page.addInitScript((session) => {
      localStorage.setItem('dinein.kitchen.device.v1', JSON.stringify(session));
    }, deviceSession);
    await page.goto('/kitchen');
    await expect(page.getByTestId('enable-sound')).toBeVisible();
    await page.getByRole('button', { name: 'Not now' }).click();
    await expect(page.getByTestId('enable-sound')).toHaveCount(0);
    expect(await page.evaluate(() => localStorage.getItem('dinein.kitchen.sound.v1'))).toBe('off');
  });
});

test.describe('kitchen responsive layout (no horizontal scroll)', () => {
  test.skip(({ isMobile }) => isMobile, 'runs once, with explicit viewports');

  for (const width of [360, 768, 1024, 1920]) {
    test(`board at ${width}px`, async ({ page }, testInfo) => {
      const height = width < 768 ? 800 : width >= 1920 ? 1080 : 768;
      await page.setViewportSize({ width, height });
      await mockKitchenBackend(page);
      await page.addInitScript(rememberDevice, deviceSession);
      await page.goto('/kitchen');
      await expect(page.getByTestId('ticket-1')).toBeVisible();

      const overflow = await page.evaluate(() => {
        const el = document.scrollingElement ?? document.documentElement;
        return el.scrollWidth - el.clientWidth;
      });
      expect(overflow, 'page should not scroll horizontally').toBeLessThanOrEqual(0);
      // Tabs below 768px, three columns from 768px.
      await expect(page.getByRole('tablist', { name: 'Order columns' })).toBeVisible({
        visible: width < 768,
      });
      await page.screenshot({ path: testInfo.outputPath(`kitchen-${width}.png`) });

      await page.goto('/kitchen/login');
      // Remembered device → still the board.
      await expect(page).toHaveURL(/\/kitchen$/);
    });
  }

  test('login at 360px', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await mockKitchenBackend(page);
    await page.goto('/kitchen/login');
    await expect(page.getByLabel('Username')).toBeVisible();
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });
});

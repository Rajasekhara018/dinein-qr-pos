import { expect, Page, test } from '@playwright/test';
import { mockWaiterBackend, soundOff } from './fixtures/waiter-backend';

async function expectNoHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(() => {
    const el = document.scrollingElement ?? document.documentElement;
    return el.scrollWidth - el.clientWidth;
  });
  expect(overflow, 'page should not scroll horizontally').toBeLessThanOrEqual(0);
}

test.describe('waiter screen', () => {
  test('PIN login → Tables → new cash order for T1 → success token', async ({ page }) => {
    await page.addInitScript(soundOff);
    const mock = await mockWaiterBackend(page);

    await page.goto('/waiter');
    await expect(page).toHaveURL(/\/waiter\/login\?returnUrl=%2Fwaiter$/);

    await page.getByLabel('Username').fill('ravi');
    await page.getByLabel('PIN (4–6 digits)').fill('0000');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.getByTestId('waiter-login-error')).toContainText('Wrong username or PIN');

    await page.getByLabel('PIN (4–6 digits)').fill('1234');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page).toHaveURL(/\/waiter$/);
    expect(mock.loginRequests.at(-1)).toEqual({ username: 'ravi', pin: '1234' });

    // Header: restaurant, staff, live connection authenticated with the staff JWT.
    await expect(page.getByTestId('waiter-restaurant')).toHaveText('Spice Route');
    await expect(page.getByTestId('waiter-staff-name')).toHaveText('Ravi');
    await expect(page.getByTestId('connection-status')).toHaveAttribute('aria-label', /Live/);
    expect(mock.connectFrames.some((f) => f.includes('Authorization:Bearer jwt-waiter'))).toBe(true);

    // Tables → T1 → menu → add two dosas → cash.
    await page.getByTestId('nav-tables').click();
    await expect(page).toHaveURL(/\/waiter\/tables$/);
    await page.getByTestId('table-tile-T1').click();
    await expect(page).toHaveURL(/\/waiter\/new\?table=1$/);
    await expect(page.getByTestId('staff-order-where')).toContainText('Table T1');

    // Unavailable items are listed but cannot be added.
    await expect(page.getByTestId('staff-item-3')).toContainText('Not available');
    await expect(page.getByRole('button', { name: 'Not available: Egg Bhurji' })).toBeDisabled();

    await page.getByRole('button', { name: 'Add Masala Dosa' }).click();
    await page.getByRole('button', { name: 'Add Masala Dosa' }).click();
    const review = page.getByTestId('staff-review-order');
    if (await review.isVisible()) await review.click();
    await expect(page.getByTestId('staff-estimate-total')).toHaveText('₹252.00');
    await expect(page.getByTestId('pay-method-CASH').getByRole('radio')).toBeChecked();
    await page.getByLabel('Guest name').fill('Asha');
    await page.getByTestId('staff-place-order').click();

    await expect(page.getByTestId('staff-order-success')).toBeVisible();
    await expect(page.getByTestId('staff-success-token')).toHaveText('41');
    await expect(page.getByTestId('staff-success-amount')).toHaveText('₹252.00');

    expect(mock.placeRequests).toHaveLength(1);
    const body = mock.placeRequests[0].body;
    expect(body['idempotencyKey']).toMatch(/^[A-Za-z0-9_-]{8,64}$/);
    expect(body).toEqual({
      tableId: 1,
      orderType: 'DINE_IN',
      items: [{ itemId: 1, variantId: null, addonIds: [], quantity: 2, notes: null }],
      note: null,
      customerName: 'Asha',
      customerPhone: null,
      paymentMethod: 'CASH',
      idempotencyKey: body['idempotencyKey'],
    });

    // Next order starts from the table step.
    await page.getByTestId('staff-new-order').click();
    await expect(page.getByTestId('table-tile-T1')).toBeVisible();
  });

  test('a READY order pushed over STOMP appears on the Ready tab → Served', async ({ page }) => {
    await page.addInitScript(soundOff);
    const mock = await mockWaiterBackend(page, { loggedIn: true });

    await page.goto('/waiter');
    await expect(page.getByTestId('waiter-order-11')).toBeVisible();
    await expect(page.getByTestId('connection-status')).toHaveAttribute('aria-label', /Live/);
    // Only READY orders on this tab.
    await expect(page.getByTestId('waiter-order-12')).toHaveCount(0);
    await expect(page.getByTestId('nav-ready-count')).toContainText('1');

    const pushed = {
      id: 21,
      orderNumber: '260927-021',
      displayToken: 21,
      status: 'READY',
      orderType: 'TAKEAWAY',
      tableId: 1,
      tableLabel: 'T1',
      placedByStaff: true,
      items: [{ name: 'Masala Dosa', foodType: 'VEG', addons: [], quantity: 1 }],
      paidAt: new Date(Date.now() - 10 * 60_000).toISOString(),
      readyAt: new Date().toISOString(),
    };
    mock.orders.push(pushed);
    mock.push('/topic/kitchen/orders', {
      type: 'ORDER_STATUS_CHANGED',
      orderId: 21,
      status: 'READY',
      order: pushed,
      at: pushed.readyAt,
    });

    const card = page.getByTestId('waiter-order-21');
    await expect(card).toBeVisible();
    await expect(card.getByTestId('waiter-order-new')).toBeVisible();
    await expect(card).toContainText('Takeaway');
    await expect(card).toContainText('Staff');
    // Oldest ready first: #11 (3 min) before #21 (just now).
    await expect(page.getByTestId('waiter-order-token')).toHaveText([/#11/, /#21/]);

    await card.getByRole('button', { name: 'Served token 21' }).click();
    await expect(card).toHaveCount(0);
    expect(mock.serveRequests).toEqual([{ id: 21, auth: 'Bearer jwt-waiter' }]);
    await expect(page.getByTestId('waiter-order-11')).toBeVisible();
  });

  test('Active tab filters by table; a notification shows a toast', async ({ page }) => {
    await page.addInitScript(soundOff);
    const mock = await mockWaiterBackend(page, { loggedIn: true });
    await page.goto('/waiter/active');
    await expect(page.getByTestId('waiter-order-11')).toBeVisible();
    await expect(page.getByTestId('waiter-order-13')).toBeVisible();
    await page.getByTestId('active-filter-T3').click();
    await expect(page.getByTestId('waiter-order-11')).toHaveCount(0);
    await expect(page.getByTestId('waiter-order-13')).toBeVisible();

    await expect(page.getByTestId('connection-status')).toHaveAttribute('aria-label', /Live/);
    mock.push('/topic/waiter/notifications', {
      type: 'NOTIFICATION',
      audience: 'STAFF_ROLE',
      recipient: 'WAITER',
      notification: {
        id: 5,
        event: 'ORDER_READY',
        title: 'Order #11 is ready, table T2',
        link: '/waiter',
        severity: 'INFO',
        orderId: 11,
        read: false,
        createdAt: new Date().toISOString(),
      },
    });
    await expect(page.getByText('Order #11 is ready, table T2')).toBeVisible();
    await expect(page.getByTestId('waiter-bell-badge')).toHaveText('1');
  });
});

test.describe('waiter responsive layout (no horizontal scroll)', () => {
  test.skip(({ isMobile }) => isMobile, 'runs once, with explicit viewports');

  for (const width of [360, 768, 1024]) {
    test(`waiter screens at ${width}px`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: width < 768 ? 800 : 900 });
      await page.addInitScript(soundOff);
      await mockWaiterBackend(page, { loggedIn: true });

      await page.goto('/waiter');
      await expect(page.getByTestId('waiter-order-11')).toBeVisible();
      await expectNoHorizontalScroll(page);
      await page.screenshot({ path: testInfo.outputPath(`waiter-ready-${width}.png`), fullPage: true });

      await page.goto('/waiter/active');
      await expect(page.getByTestId('waiter-order-13')).toBeVisible();
      await expectNoHorizontalScroll(page);

      await page.goto('/waiter/tables');
      await expect(page.getByTestId('table-tile-Terrace 12')).toBeVisible();
      await expectNoHorizontalScroll(page);
      await page.screenshot({ path: testInfo.outputPath(`waiter-tables-${width}.png`), fullPage: true });

      await page.goto('/waiter/new?table=1');
      await expect(page.getByRole('button', { name: 'Add Masala Dosa' })).toBeVisible();
      await page.getByRole('button', { name: 'Add Masala Dosa' }).click();
      await expectNoHorizontalScroll(page);
      const review = page.getByTestId('staff-review-order');
      if (await review.isVisible()) await review.click();
      await expect(page.getByTestId('staff-place-order')).toBeVisible();
      await expectNoHorizontalScroll(page);
      await page.screenshot({ path: testInfo.outputPath(`waiter-new-${width}.png`), fullPage: true });

      await page.goto('/waiter/orders/41');
      await expect(page.getByTestId('waiter-order-page-token')).toHaveText('41');
      await expectNoHorizontalScroll(page);

      await page.goto('/waiter/login');
      // Signed in → the login is skipped.
      await expect(page).toHaveURL(/\/waiter$/);
    });
  }

  test('login at 360px', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await mockWaiterBackend(page);
    await page.goto('/waiter/login');
    await expect(page.getByLabel('Username')).toBeVisible();
    await expectNoHorizontalScroll(page);
  });
});

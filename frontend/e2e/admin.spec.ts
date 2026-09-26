import { expect, Page, test } from '@playwright/test';
import { mockAdminBackend } from './fixtures/admin-mock-backend';

/** Opens a section from the sidebar (desktop) or the hamburger drawer (phones). */
async function openSection(page: Page, label: string): Promise<void> {
  const toggle = page.getByTestId('nav-toggle');
  if (await toggle.isVisible()) await toggle.click();
  await page.getByRole('navigation', { name: 'Admin sections' }).getByRole('link', { name: label }).first().click();
}

test.describe('admin panel', () => {
  test('login → forced password change → dashboard → create an item with sizes', async ({ page }) => {
    const state = await mockAdminBackend(page, { mustChangePassword: true });

    await page.goto('/admin');
    await expect(page).toHaveURL(/\/admin\/login\?returnUrl=%2Fadmin/);

    await page.getByLabel('Username').fill('owner');
    await page.getByLabel('Password', { exact: true }).fill('wrong-pass1');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByTestId('login-error')).toContainText('Incorrect username or password');

    await page.getByLabel('Password', { exact: true }).fill('Owner@2026x');
    await page.getByRole('button', { name: 'Sign in' }).click();

    // Forced password change.
    await expect(page).toHaveURL(/\/admin\/change-password$/);
    await expect(page.getByRole('heading', { name: 'Set a new password' })).toBeVisible();
    await page.getByLabel('Current password').fill('Owner@2026x');
    await page.getByLabel('New password', { exact: true }).fill('weak');
    await page.getByLabel('Confirm new password').fill('weak');
    await page.getByRole('button', { name: 'Save new password' }).click();
    await expect(page.getByText('Use 8–72 characters with at least one letter and one digit.')).toBeVisible();
    await page.getByLabel('New password', { exact: true }).fill('NewSecret2026');
    await page.getByLabel('Confirm new password').fill('NewSecret2026');
    await page.getByRole('button', { name: 'Save new password' }).click();

    // Dashboard.
    await expect(page).toHaveURL(/\/admin$/);
    await expect(page.getByTestId('kpi-orders')).toHaveText('42');
    await expect(page.getByTestId('kpi-revenue')).toContainText('18,450.50');
    await expect(page.getByTestId('flagged-alert')).toBeVisible();
    await expect(page.getByTestId('notifications-badge')).toHaveText('2');
    expect(state.requests.find((r) => r.path === '/api/auth/change-password')?.body).toEqual({
      currentPassword: 'Owner@2026x',
      newPassword: 'NewSecret2026',
    });

    // Create an item with sizes.
    await openSection(page, 'Items');
    await expect(page.getByText('Masala Dosa').locator('visible=true').first()).toBeVisible();
    await page.getByTestId('add-item').click();
    await expect(page.getByRole('heading', { name: 'New item' })).toBeVisible();

    await page.getByTestId('item-name').fill('Paneer Tikka');
    await page.getByTestId('item-category').selectOption({ label: 'Starters' });
    await page.getByTestId('item-base-price').fill('150');
    await page.getByRole('switch', { name: /Has sizes/ }).click();

    const row0 = page.getByTestId('variant-row-0');
    await expect(row0.getByLabel('Price (₹)')).toHaveValue('150');
    await row0.getByLabel('Size name').fill('Half');
    await page.getByTestId('add-variant').click();
    const row1 = page.getByTestId('variant-row-1');
    await row1.getByLabel('Size name').fill('Full');
    await row1.getByLabel('Price (₹)').fill('280');

    // Live preview of the guest card.
    const preview = page.getByTestId('item-preview');
    await expect(preview).toContainText('Paneer Tikka');
    await expect(preview).toContainText('from');

    await page.getByTestId('item-save').click();

    await expect(page).toHaveURL(/\/admin\/menu\/items\?categoryId=10/);
    await expect(page.getByText('Paneer Tikka').locator('visible=true').first()).toBeVisible();
    const created = state.requests.find((r) => r.method === 'POST' && r.path === '/api/admin/items');
    expect(created?.body).toMatchObject({
      categoryId: 10,
      name: 'Paneer Tikka',
      basePrice: null,
      foodType: 'VEG',
      gstPercent: 5,
      variants: [
        { id: null, name: 'Half', price: 150, isDefault: true },
        { id: null, name: 'Full', price: 280, isDefault: false },
      ],
      version: null,
    });
  });

  test('inline price edit with Undo', async ({ page }) => {
    const state = await mockAdminBackend(page, { loggedIn: true });
    await page.goto('/admin/menu/items');

    await page.getByTestId('price-edit-1').locator('visible=true').click();
    const input = page.getByTestId('price-input-1-base').locator('visible=true');
    await expect(input).toBeFocused();
    await input.fill('135.50');
    await input.press('Enter');

    await expect(page.getByTestId('price-1').locator('visible=true')).toHaveText('₹135.50');
    const toast = page.getByRole('status').filter({ hasText: 'Price of Masala Dosa updated' });
    await expect(toast).toBeVisible();
    await toast.getByRole('button', { name: 'Undo' }).click();

    await expect(page.getByTestId('price-1').locator('visible=true')).toHaveText('₹120.00');
    const patches = state.requests.filter((r) => r.method === 'PATCH' && r.path === '/api/admin/items/1/price');
    expect(patches.map((p) => p.body)).toEqual([
      { basePrice: 135.5, version: 3 },
      { basePrice: 120, version: 4 },
    ]);
  });
});

test.describe('admin responsive layout (no horizontal scroll)', () => {
  test.skip(({ isMobile }) => isMobile, 'runs once, with explicit viewports');

  async function expectNoHorizontalScroll(page: Page): Promise<void> {
    const overflow = await page.evaluate(() => {
      const el = document.scrollingElement ?? document.documentElement;
      return el.scrollWidth - el.clientWidth;
    });
    expect(overflow, 'page should not scroll horizontally').toBeLessThanOrEqual(0);
  }

  for (const width of [360, 768, 1024, 1920]) {
    test(`dashboard, items and orders at ${width}px`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: width < 768 ? 800 : 900 });
      await mockAdminBackend(page, { loggedIn: true });

      await page.goto('/admin');
      await expect(page.getByTestId('kpi-orders')).toHaveText('42');
      await expectNoHorizontalScroll(page);
      await page.screenshot({ path: testInfo.outputPath(`admin-dashboard-${width}.png`), fullPage: true });

      await page.goto('/admin/menu/items');
      await expect(page.getByText('Chicken Biryani').locator('visible=true').first()).toBeVisible();
      await expectNoHorizontalScroll(page);
      await page.screenshot({ path: testInfo.outputPath(`admin-items-${width}.png`), fullPage: true });

      await page.goto('/admin/orders');
      await expect(page.getByText('260927-041').locator('visible=true').first()).toBeVisible();
      await expectNoHorizontalScroll(page);
      await page.screenshot({ path: testInfo.outputPath(`admin-orders-${width}.png`), fullPage: true });

      // Mobile shows cards, desktop shows tables.
      if (width < 768) {
        await expect(page.getByTestId('order-card-41')).toBeVisible();
        await expect(page.getByTestId('order-row-41')).toBeHidden();
      } else {
        await expect(page.getByTestId('order-row-41')).toBeVisible();
      }
    });
  }
});

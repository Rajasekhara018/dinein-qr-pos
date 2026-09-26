import { expect, Page, test } from '@playwright/test';
import { mockBackend, razorpayStub } from './fixtures/mock-backend';

/** Widths from REQUIREMENTS §10.1. */
const WIDTHS = [360, 390, 414, 768, 1024, 1280, 1920];

async function expectNoHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(() => {
    const el = document.scrollingElement ?? document.documentElement;
    return el.scrollWidth - el.clientWidth;
  });
  expect(overflow, 'page should not scroll horizontally').toBeLessThanOrEqual(0);
}

async function seedCart(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Add Masala Dosa' }).click();
  await page.getByRole('button', { name: 'Add Chicken Biryani' }).click();
  await page.getByRole('dialog').getByTestId('item-sheet-confirm').click();
}

test.describe('responsive layout (no horizontal scroll)', () => {
  test.skip(({ isMobile }) => isMobile, 'runs once, with explicit viewports');

  for (const width of WIDTHS) {
    test(`guest pages at ${width}px`, async ({ page }, testInfo) => {
      await page.setViewportSize({ width, height: width < 768 ? 800 : 900 });
      await page.addInitScript(razorpayStub);
      await mockBackend(page);

      await page.goto('/menu?t=abc');
      await expect(page.getByRole('heading', { name: 'Starters' })).toBeVisible();
      await seedCart(page);
      await expectNoHorizontalScroll(page);
      await page.screenshot({ path: testInfo.outputPath(`menu-${width}.png`), fullPage: true });

      await page.getByRole('button', { name: 'Increase Chicken Biryani' }).first().click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await page.screenshot({ path: testInfo.outputPath(`sheet-${width}.png`) });
      await page.keyboard.press('Escape');

      await page.goto('/menu/cart');
      await expect(page.getByTestId('bill-total')).toBeVisible();
      await expectNoHorizontalScroll(page);
      await page.screenshot({ path: testInfo.outputPath(`cart-${width}.png`), fullPage: true });

      await page.goto('/menu/orders/42');
      await expect(page.getByTestId('order-token')).toHaveText('42');
      await expectNoHorizontalScroll(page);
      await page.screenshot({ path: testInfo.outputPath(`order-${width}.png`), fullPage: true });
    });
  }
});

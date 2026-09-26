import { expect, test } from '@playwright/test';
import { mockBackend, razorpayStub } from './fixtures/mock-backend';

test.describe('guest happy path', () => {
  test('scan → add items (incl. variants) → cart → pay → order status shows the token', async ({ page, isMobile }) => {
    await page.addInitScript(razorpayStub);
    const backend = await mockBackend(page);

    // 1. Scan the table QR.
    await page.goto('/menu?t=abc');
    await expect(page).toHaveURL(/\/menu$/); // `t` is dropped after the session is established
    await expect(page.getByText('Spice Route').first()).toBeVisible();
    await expect(page.getByText('T3').first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Starters' })).toBeVisible();

    // Out-of-stock items cannot be added.
    await expect(page.getByTestId('menu-item-3').getByText('Out of stock')).toBeVisible();

    // 2. Add a simple item twice.
    await page.getByRole('button', { name: 'Add Masala Dosa' }).click();
    await page.getByRole('button', { name: 'Increase Masala Dosa' }).first().click();

    // 3. Add an item with variants + addon via the bottom sheet.
    await page.getByRole('button', { name: 'Add Chicken Biryani' }).click();
    const sheet = page.getByRole('dialog');
    await expect(sheet.getByRole('heading', { name: 'Chicken Biryani' })).toBeVisible();
    await sheet.getByLabel('Full').check();
    await sheet.getByLabel('Extra raita').check();
    await expect(sheet.getByTestId('item-sheet-total')).toHaveText('₹350.00');
    await sheet.getByTestId('item-sheet-confirm').click();
    await expect(sheet).toBeHidden();

    // 4. Go to the cart.
    if (isMobile) {
      await expect(page.getByTestId('cart-bar')).toContainText('3 items');
      await page.getByTestId('cart-bar').click();
    } else {
      await page.getByRole('link', { name: /Checkout/ }).click();
    }
    await expect(page).toHaveURL(/\/menu\/cart$/);
    await expect(page.getByTestId('bill-subtotal')).toHaveText('₹590.00');
    await expect(page.getByTestId('bill-cgst')).toHaveText('₹14.75');
    await expect(page.getByTestId('bill-sgst')).toHaveText('₹14.75');
    await expect(page.getByTestId('bill-total')).toHaveText('₹619.50');

    // Inline phone validation.
    await page.getByLabel('Mobile number').fill('12345');
    await page.getByLabel('Your name').click();
    await expect(page.getByTestId('phone-error')).toBeVisible();
    await page.getByLabel('Mobile number').fill('9876543210');
    await expect(page.getByTestId('phone-error')).toBeHidden();
    await page.getByLabel('Your name').fill('Asha');

    // 5. Pay (Razorpay stub succeeds immediately → verify → order page).
    await page.getByTestId('pay-button').click();
    await expect(page).toHaveURL(/\/menu\/orders\/42$/);
    await expect(page.getByTestId('order-token')).toHaveText('42');
    await expect(page.getByText('Preparing').first()).toBeVisible();

    // The backend got ids only, with an idempotency key, and the verify call carried the Razorpay response.
    expect(backend.placeOrderRequests).toHaveLength(1);
    expect(backend.placeOrderRequests[0].headers['idempotency-key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(backend.placeOrderRequests[0].body).toEqual({
      items: [
        { itemId: 1, variantId: null, addonIds: [], quantity: 2, notes: null },
        { itemId: 2, variantId: 22, addonIds: [31], quantity: 1, notes: null },
      ],
      notes: null,
      customerName: 'Asha',
      customerPhone: '9876543210',
    });
    expect(backend.verifyRequests).toEqual([
      { razorpay_order_id: 'order_TEST42', razorpay_payment_id: 'pay_TEST123', razorpay_signature: 'sig_test' },
    ]);

    // The cart was cleared after payment.
    await page.getByRole('link', { name: 'Order more' }).click();
    await expect(page.getByTestId('cart-bar')).toHaveCount(0);
  });

  test('invalid QR shows the friendly scan page', async ({ page }) => {
    await mockBackend(page);
    await page.goto('/menu?t=wrong');
    await expect(page).toHaveURL(/\/menu\/scan$/);
    await expect(page.getByRole('heading', { name: 'Please scan the QR code on your table' })).toBeVisible();
  });
});

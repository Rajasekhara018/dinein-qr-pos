import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../../../core/api/api-error';
import { CheckoutResponse } from '../../../core/api/models';
import { biryani, dosa, menu, menuItem } from '../../guest/data/test-fixtures';
import { StaffCartStore } from './staff-cart.store';
import {
  buildStaffOrderRequest,
  checkoutBranch,
  draftFingerprint,
  draftProblem,
  placeErrorMessage,
  StaffOrderDraft,
} from './staff-order';

describe('StaffCartStore', () => {
  let cart: StaffCartStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [StaffCartStore] });
    cart = TestBed.inject(StaffCartStore);
  });

  it('builds priced lines, merges identical selections and estimates the bill', () => {
    cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 1 });
    cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 1 });
    cart.add(biryani, { variantId: 22, addonIds: [32, 31], notes: ' less  spicy ', quantity: 1 });

    expect(cart.lines()).toHaveLength(2);
    expect(cart.itemCount()).toBe(3);
    expect(cart.quantityByItem().get(1)).toBe(2);
    const line = cart.lines()[1];
    expect(line).toMatchObject({
      variantName: 'Full',
      addonIds: [31, 32],
      addonNames: ['Extra raita', 'Boiled egg'],
      notes: 'less spicy',
      unitPrice: 375.5,
    });
    // 240 + 375.50 = 615.50; GST 5 % exclusive per line: 12.00 + 18.78 → 646.28
    expect(cart.bill().subtotal).toBe(61550);
    expect(cart.bill().grandTotal).toBe(64628);
  });

  it('refuses unavailable items', () => {
    const soldOut = menuItem({ id: 5, name: 'Egg Bhurji', available: false });
    expect(cart.add(soldOut, { variantId: null, addonIds: [], notes: '', quantity: 1 })).toBeNull();
    expect(cart.isEmpty()).toBe(true);
  });

  it('marks ITEM_UNAVAILABLE lines by request index and leaves them out of the request', () => {
    cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 2 });
    cart.add(biryani, { variantId: 21, addonIds: [], notes: '', quantity: 1 });
    const { keys } = cart.toItems();
    cart.markProblems([{ lineIndex: 1, itemId: 2, reason: 'ITEM_UNAVAILABLE' }], keys);
    expect(cart.hasIssues()).toBe(true);
    expect(cart.lines()[1].issue?.message).toBe('Out of stock');
    expect(cart.toItems().items).toEqual([
      { itemId: 1, variantId: null, addonIds: [], quantity: 2, notes: null },
    ]);
    cart.removeUnavailable();
    expect(cart.lines()).toHaveLength(1);
  });

  it('reconciles with a fresh menu (item now unavailable)', () => {
    cart.add(dosa, { variantId: null, addonIds: [], notes: '', quantity: 1 });
    cart.reconcile(menu([{ ...dosa, available: false }, biryani], true));
    expect(cart.lines()[0].issue?.reason).toBe('ITEM_UNAVAILABLE');
    expect(cart.pricesIncludeGst()).toBe(true);
  });
});

describe('buildStaffOrderRequest', () => {
  const draft: StaffOrderDraft = {
    tableId: 3,
    orderType: 'DINE_IN',
    items: [
      { itemId: 1, variantId: null, addonIds: [], quantity: 2, notes: null },
      { itemId: 2, variantId: 22, addonIds: [31], quantity: 1, notes: 'no onion' },
    ],
    note: '  birthday table ',
    customerName: ' Asha ',
    customerPhone: '98765 43210',
    paymentMethod: 'CASH',
  };

  it('produces the exact JSON body', () => {
    const body = buildStaffOrderRequest(draft, 'key-123456');
    expect(JSON.stringify(body)).toBe(
      JSON.stringify({
        tableId: 3,
        orderType: 'DINE_IN',
        items: [
          { itemId: 1, variantId: null, addonIds: [], quantity: 2, notes: null },
          { itemId: 2, variantId: 22, addonIds: [31], quantity: 1, notes: 'no onion' },
        ],
        note: 'birthday table',
        customerName: 'Asha',
        customerPhone: '9876543210',
        paymentMethod: 'CASH',
        idempotencyKey: 'key-123456',
      }),
    );
  });

  it('sends nulls for blank optional fields and no table for takeaway', () => {
    const body = buildStaffOrderRequest(
      {
        ...draft,
        tableId: null,
        orderType: 'TAKEAWAY',
        note: '   ',
        customerName: '',
        customerPhone: '',
        paymentMethod: 'ONLINE',
      },
      'key-abcdefgh',
    );
    expect(body).toEqual({
      tableId: null,
      orderType: 'TAKEAWAY',
      items: draft.items,
      note: null,
      customerName: null,
      customerPhone: null,
      paymentMethod: 'ONLINE',
      idempotencyKey: 'key-abcdefgh',
    });
  });

  it('fingerprints everything but the key', () => {
    expect(draftFingerprint(draft)).toBe(draftFingerprint({ ...draft }));
    expect(draftFingerprint(draft)).not.toBe(draftFingerprint({ ...draft, paymentMethod: 'ONLINE' }));
  });

  it('explains what blocks placing', () => {
    expect(draftProblem({ tableId: null, orderType: 'DINE_IN', lineCount: 1, hasIssues: false })).toContain(
      'table',
    );
    expect(draftProblem({ tableId: null, orderType: 'TAKEAWAY', lineCount: 1, hasIssues: false })).toBeNull();
    expect(draftProblem({ tableId: 1, orderType: 'DINE_IN', lineCount: 0, hasIssues: false })).toContain(
      'item',
    );
    expect(draftProblem({ tableId: 1, orderType: 'DINE_IN', lineCount: 2, hasIssues: true })).toContain(
      'no longer available',
    );
  });
});

describe('checkoutBranch (payment method branching)', () => {
  const base: CheckoutResponse = {
    orderId: 42,
    orderNumber: '260927-042',
    displayToken: 42,
    status: 'CONFIRMED',
  };

  it('offline methods: CONFIRMED + provider OFFLINE → success screen', () => {
    expect(checkoutBranch({ ...base, provider: 'OFFLINE', amountPaise: 25200 })).toBe('confirmed');
  });

  it('online: PENDING_PAYMENT with a checkout → CheckoutService', () => {
    expect(
      checkoutBranch({
        ...base,
        status: 'PENDING_PAYMENT',
        provider: 'RAZORPAY',
        mode: 'SDK',
        checkout: { key: 'k' },
      }),
    ).toBe('checkout');
  });

  it('idempotent replay of a paid online order → order page', () => {
    expect(checkoutBranch(base)).toBe('already-paid');
  });

  it('maps staff errors to messages', () => {
    expect(placeErrorMessage(new ApiError(400, 'TABLE_REQUIRED', ''))).toContain('table');
    expect(placeErrorMessage(new ApiError(400, 'TAKEAWAY_DISABLED', ''))).toContain('Takeaway');
    expect(placeErrorMessage(new ApiError(503, 'ORDERING_CLOSED', 'Closed now'))).toBe('Closed now');
  });
});

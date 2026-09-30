import { describe, expect, it } from 'vitest';
import { KioskCounterOrder } from '../../../core/api/models';
import {
  addonsLine,
  filterByToken,
  isExpired,
  itemTitle,
  normalizeTokenQuery,
  paymentMethod,
  removeKioskOrder,
  sortOldestFirst,
  waitedMinutes,
} from './kiosk-counter-logic';

const order = (
  id: number,
  token: number | string,
  over: Partial<KioskCounterOrder> = {},
): KioskCounterOrder => ({
  id,
  orderNumber: `K-${id}`,
  displayToken: token,
  orderType: 'DINE_IN',
  status: 'PENDING_PAYMENT',
  grandTotal: 540,
  placedAt: `2026-09-30T10:0${id}:00Z`,
  items: [],
  ...over,
});

describe('token search', () => {
  const list = [order(1, 12), order(2, 112), order(3, 7)];

  it('returns everything for an empty search', () => {
    expect(filterByToken(list, '')).toHaveLength(3);
    expect(filterByToken(list, '  ')).toHaveLength(3);
  });

  it('matches tokens containing the digits and ignores # and spaces', () => {
    expect(filterByToken(list, '12').map((o) => o.id)).toEqual([1, 2]);
    expect(filterByToken(list, '#7').map((o) => o.id)).toEqual([3]);
    expect(filterByToken(list, ' 1 1 ').map((o) => o.id)).toEqual([2]);
    expect(filterByToken(list, '999')).toEqual([]);
  });

  it('normalises the query', () => {
    expect(normalizeTokenQuery(' #A-12 ')).toBe('a12');
  });
});

describe('list helpers', () => {
  it('removes a paid order', () => {
    expect(removeKioskOrder([order(1, 1), order(2, 2)], 1).map((o) => o.id)).toEqual([2]);
  });

  it('sorts oldest first, ties by id', () => {
    const list = [
      order(3, 3, { placedAt: '2026-09-30T10:05:00Z' }),
      order(2, 2, { placedAt: '2026-09-30T10:01:00Z' }),
      order(1, 1, { placedAt: '2026-09-30T10:05:00Z' }),
    ];
    expect(sortOldestFirst(list).map((o) => o.id)).toEqual([2, 1, 3]);
  });

  it('flags expired orders (still payable)', () => {
    expect(isExpired({ status: 'EXPIRED' })).toBe(true);
    expect(isExpired({ status: 'PENDING_PAYMENT' })).toBe(false);
  });

  it('computes minutes waited', () => {
    const now = Date.parse('2026-09-30T10:10:30Z');
    expect(waitedMinutes('2026-09-30T10:00:00Z', now)).toBe(10);
    expect(waitedMinutes('2026-09-30T11:00:00Z', now)).toBe(0);
    expect(waitedMinutes('bad', now)).toBe(0);
  });
});

describe('item text and payment methods', () => {
  it('formats item titles and add-ons', () => {
    expect(itemTitle({ name: 'Burger', variantName: 'Large', quantity: 2 })).toBe(
      '2 × Burger (Large)',
    );
    expect(itemTitle({ name: 'Fries', variantName: null, quantity: 1 })).toBe('1 × Fries');
    expect(addonsLine({ addons: ['Cheese', 'Bacon'] })).toBe('+ Cheese, Bacon');
    expect(addonsLine({ addons: [] })).toBe('');
  });

  it('describes payment methods for the confirm step', () => {
    expect(paymentMethod('CASH').phrase).toBe('in cash');
    expect(paymentMethod('UPI_AT_COUNTER').label).toBe('UPI');
    expect(paymentMethod('CARD_AT_COUNTER').phrase).toBe('by card');
  });
});

import { describe, expect, it } from 'vitest';
import {
  ageLevel,
  applyStatus,
  formatElapsed,
  groupByColumn,
  isAutoHidden,
  mergeFetched,
  nextAction,
  removeOrder,
  sortOldestFirst,
  upsertOrder,
  visibleOrders,
} from './board-state';
import { kOrder, MIN, T0, testConfig } from './test-fixtures';

describe('board-state', () => {
  it('sorts oldest paid first, orders without paidAt last, ties by id', () => {
    const a = kOrder({ id: 1 }, 2);
    const b = kOrder({ id: 2 }, 8);
    const c = kOrder({ id: 3, paidAt: undefined });
    const d = kOrder({ id: 4 }, 8);
    expect(sortOldestFirst([a, c, d, b]).map((o) => o.id)).toEqual([2, 4, 1, 3]);
  });

  it('upsert inserts in order, replaces existing, and removes orders that left the kitchen', () => {
    const list = [kOrder({ id: 1 }, 10), kOrder({ id: 2 }, 5)];
    const inserted = upsertOrder(list, kOrder({ id: 3 }, 7));
    expect(inserted.map((o) => o.id)).toEqual([1, 3, 2]);

    const replaced = upsertOrder(inserted, { ...inserted[1], status: 'PREPARING' });
    expect(replaced.find((o) => o.id === 3)?.status).toBe('PREPARING');
    expect(replaced).toHaveLength(3);

    expect(upsertOrder(replaced, { ...replaced[0], status: 'COMPLETED' }).map((o) => o.id)).toEqual(
      [3, 2],
    );
    expect(upsertOrder(replaced, { ...replaced[0], status: 'CANCELLED' })).toHaveLength(2);
    expect(removeOrder(replaced, 2).map((o) => o.id)).toEqual([1, 3]);
  });

  it('applyStatus moves locally, stamps times, and drops completed orders', () => {
    const list = [kOrder({ id: 1 })];
    const preparing = applyStatus(list, 1, 'PREPARING', T0);
    expect(preparing[0].status).toBe('PREPARING');
    expect(preparing[0].preparingAt).toBe(new Date(T0).toISOString());
    const ready = applyStatus(preparing, 1, 'READY', T0 + MIN);
    expect(ready[0].readyAt).toBe(new Date(T0 + MIN).toISOString());
    expect(applyStatus(ready, 1, 'COMPLETED', T0)).toEqual([]);
    expect(applyStatus(ready, 99, 'READY', T0)).toEqual(ready);
  });

  it('groups by column, oldest first in every column', () => {
    const groups = groupByColumn([
      kOrder({ id: 1, status: 'READY' }, 3),
      kOrder({ id: 2, status: 'CONFIRMED' }, 1),
      kOrder({ id: 3, status: 'CONFIRMED' }, 4),
      kOrder({ id: 4, status: 'PREPARING' }, 2),
      kOrder({ id: 5, status: 'READY' }, 9),
    ]);
    expect(groups.CONFIRMED.map((o) => o.id)).toEqual([3, 2]);
    expect(groups.PREPARING.map((o) => o.id)).toEqual([4]);
    expect(groups.READY.map((o) => o.id)).toEqual([5, 1]);
  });

  it('auto-hides READY orders after readyAutoHideMinutes', () => {
    const ready = kOrder({ id: 1, status: 'READY', readyAt: new Date(T0 - 4 * MIN).toISOString() });
    const prep = kOrder({ id: 2, status: 'PREPARING' }, 60);
    expect(isAutoHidden(ready, T0, 5)).toBe(false);
    expect(isAutoHidden(ready, T0 + MIN, 5)).toBe(true);
    expect(isAutoHidden(prep, T0, 5)).toBe(false);
    expect(visibleOrders([ready, prep], T0 + 2 * MIN, testConfig).map((o) => o.id)).toEqual([2]);
  });

  it('escalates age: ok → warn (amber) → alert (red); READY never escalates', () => {
    const o = kOrder({ id: 1 }, 0);
    expect(ageLevel(o, T0 + 10 * MIN, testConfig)).toBe('ok');
    expect(ageLevel(o, T0 + 10 * MIN + 1000, testConfig)).toBe('warn');
    expect(ageLevel(o, T0 + 20 * MIN + 1000, testConfig)).toBe('alert');
    expect(ageLevel({ ...o, status: 'READY' }, T0 + 60 * MIN, testConfig)).toBe('ok');
    expect(ageLevel({ ...o, paidAt: undefined }, T0 + 60 * MIN, testConfig)).toBe('ok');
  });

  it('mergeFetched lets the server win except for pending orders', () => {
    const current = [kOrder({ id: 1, status: 'PREPARING' }), kOrder({ id: 2 })];
    const fetched = [
      kOrder({ id: 1, status: 'CONFIRMED' }),
      kOrder({ id: 2, status: 'PREPARING' }),
      kOrder({ id: 3 }),
    ];
    const merged = mergeFetched(fetched, current, new Set([1]));
    expect(merged.find((o) => o.id === 1)?.status).toBe('PREPARING');
    expect(merged.find((o) => o.id === 2)?.status).toBe('PREPARING');
    expect(merged.map((o) => o.id).sort()).toEqual([1, 2, 3]);
    // Optimistically removed (served) and still pending: stays removed.
    expect(mergeFetched(fetched, [current[1]], new Set([1])).some((o) => o.id === 1)).toBe(false);
  });

  it('maps statuses to the kitchen buttons and formats elapsed time', () => {
    expect(nextAction('CONFIRMED')).toEqual({ target: 'PREPARING', label: 'Start' });
    expect(nextAction('PREPARING')).toEqual({ target: 'READY', label: 'Ready' });
    expect(nextAction('READY')).toEqual({ target: 'COMPLETED', label: 'Served' });
    expect(nextAction('COMPLETED')).toBeNull();
    expect(formatElapsed(0)).toBe('0:00');
    expect(formatElapsed(65_000)).toBe('1:05');
    expect(formatElapsed(3_725_000)).toBe('1:02:05');
  });
});

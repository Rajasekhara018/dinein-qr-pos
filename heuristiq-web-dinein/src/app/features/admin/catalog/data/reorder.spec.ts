import { describe, expect, it } from 'vitest';
import { canMove, moveItem, orderChanged, sortByDisplayOrder } from './reorder';

describe('category reorder helpers', () => {
  const list = ['a', 'b', 'c', 'd'];

  it('moves items without mutating the input', () => {
    expect(moveItem(list, 0, 2)).toEqual(['b', 'c', 'a', 'd']);
    expect(moveItem(list, 3, 0)).toEqual(['d', 'a', 'b', 'c']);
    expect(list).toEqual(['a', 'b', 'c', 'd']);
  });

  it('clamps out-of-range indices and handles no-ops', () => {
    expect(moveItem(list, 1, 99)).toEqual(['a', 'c', 'd', 'b']);
    expect(moveItem(list, 2, 2)).toEqual(list);
    expect(moveItem([], 0, 1)).toEqual([]);
  });

  it('knows when up/down buttons are possible', () => {
    expect(canMove(0, -1, 4)).toBe(false);
    expect(canMove(0, 1, 4)).toBe(true);
    expect(canMove(3, 1, 4)).toBe(false);
    expect(canMove(3, -1, 4)).toBe(true);
  });

  it('detects real order changes', () => {
    expect(orderChanged([1, 2, 3], [1, 2, 3])).toBe(false);
    expect(orderChanged([1, 2, 3], [2, 1, 3])).toBe(true);
  });

  it('sorts by displayOrder then name', () => {
    const sorted = sortByDisplayOrder([
      { displayOrder: 2, name: 'Drinks' },
      { displayOrder: 1, name: 'Mains' },
      { displayOrder: 1, name: 'Breads' },
    ]);
    expect(sorted.map((c) => c.name)).toEqual(['Breads', 'Mains', 'Drinks']);
  });
});

import { describe, expect, it } from 'vitest';
import { deepDiff } from './change-diff.util';

describe('deepDiff', () => {
  it('opens JSON strings and lists only the leaves that changed', () => {
    const before = JSON.stringify({ highRating: { minRating: 4.8, bonus: 150 }, tripCount: [{ trips: 10, bonus: 200 }, { trips: 20, bonus: 250 }] });
    const after = JSON.stringify({ highRating: { minRating: 4.8, bonus: 150 }, tripCount: [{ trips: 10, bonus: 300 }, { trips: 20, bonus: 250 }] });
    expect(deepDiff([{ path: 'value', change: 'CHANGED', before, after }])).toEqual([
      { path: 'value.tripCount[0].bonus', kind: 'CHANGED', before: 200, after: 300 },
    ]);
  });

  it('reports added keys and array items', () => {
    const rows = deepDiff([{ path: 'v', change: 'CHANGED', before: { a: 1, b: [1] }, after: { a: 1, c: 2, b: [1, 2] } }]);
    expect(rows).toEqual([
      { path: 'v.b[1]', kind: 'ADDED', before: undefined, after: 2 },
      { path: 'v.c', kind: 'ADDED', before: undefined, after: 2 },
    ]);
  });

  it('reports removed keys, ignores unchanged values and treats REMOVED entries as having no after', () => {
    expect(deepDiff([{ path: 'v', change: 'CHANGED', before: { a: 1, b: 2 }, after: { a: 1 } }]))
      .toEqual([{ path: 'v.b', kind: 'REMOVED', before: 2, after: undefined }]);
    expect(deepDiff([{ path: 'same', change: 'CHANGED', before: 'x', after: 'x' }])).toEqual([]);
    expect(deepDiff([{ path: 'gone', change: 'REMOVED', before: 5, after: 9 }]))
      .toEqual([{ path: 'gone', kind: 'REMOVED', before: 5, after: undefined }]);
  });

  it('does not open non-container JSON or malformed JSON strings, and compares arrays against objects whole', () => {
    expect(deepDiff([{ path: 'n', change: 'CHANGED', before: '5', after: '6' }]))
      .toEqual([{ path: 'n', kind: 'CHANGED', before: '5', after: '6' }]);
    expect(deepDiff([{ path: 'j', change: 'CHANGED', before: '{bad', after: '{worse' }]))
      .toEqual([{ path: 'j', kind: 'CHANGED', before: '{bad', after: '{worse' }]);
    expect(deepDiff([{ path: 'm', change: 'CHANGED', before: [1], after: { 0: 1 } }]))
      .toEqual([{ path: 'm', kind: 'CHANGED', before: [1], after: { 0: 1 } }]);
  });

  it('compares values nested deeper than the limit as a whole', () => {
    let b: unknown = { leaf: 1 };
    let a: unknown = { leaf: 2 };
    for (let i = 0; i < 10; i++) { b = { n: b }; a = { n: a }; }
    const rows = deepDiff([{ path: 'd', change: 'CHANGED', before: b, after: a }]);
    expect(rows).toHaveLength(1);
    expect(rows[0].kind).toBe('CHANGED');
    expect(rows[0].path).toBe('d' + '.n'.repeat(8));
    expect(rows[0].before).toEqual({ n: { n: { leaf: 1 } } });
  });

  it('keeps plain values and top-level adds as they are', () => {
    expect(deepDiff([
      { path: 'action', change: 'ADDED', before: undefined, after: 'SET' },
      { path: 'n', change: 'CHANGED', before: 'abc', after: 'abd' },
    ])).toEqual([
      { path: 'action', kind: 'ADDED', before: undefined, after: 'SET' },
      { path: 'n', kind: 'CHANGED', before: 'abc', after: 'abd' },
    ]);
  });
});

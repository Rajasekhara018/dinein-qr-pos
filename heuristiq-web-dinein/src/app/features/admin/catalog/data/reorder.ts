/** Pure helpers for category ordering (drag-and-drop and up/down buttons). */

/** Returns a new array with the element at `from` moved to `to` (indices are clamped). */
export function moveItem<T>(list: readonly T[], from: number, to: number): T[] {
  const copy = [...list];
  if (copy.length === 0) return copy;
  const source = clamp(from, 0, copy.length - 1);
  const target = clamp(to, 0, copy.length - 1);
  if (source === target) return copy;
  const [moved] = copy.splice(source, 1);
  copy.splice(target, 0, moved);
  return copy;
}

/** Whether moving index by `delta` (−1 up, +1 down) stays inside the list. */
export function canMove(index: number, delta: number, length: number): boolean {
  const target = index + delta;
  return index >= 0 && index < length && target >= 0 && target < length;
}

/** True when two id sequences differ (skip the API call for no-op drops). */
export function orderChanged(before: readonly number[], after: readonly number[]): boolean {
  return before.length !== after.length || before.some((id, i) => id !== after[i]);
}

/** Sorts by `displayOrder`, then name, without mutating. */
export function sortByDisplayOrder<T extends { displayOrder: number; name: string }>(
  list: readonly T[],
): T[] {
  return [...list].sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name));
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

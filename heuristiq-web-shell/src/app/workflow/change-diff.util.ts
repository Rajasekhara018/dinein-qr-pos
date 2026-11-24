import { ChangeDiffEntry } from './workflow.models';

/** One changed leaf of a (possibly nested) value, addressed by a readable path such as `tripCount[1].bonus`. */
export interface DeepDiffRow {
  path: string;
  kind: 'ADDED' | 'REMOVED' | 'CHANGED';
  before: unknown;
  after: unknown;
}

/** Nesting deeper than this is compared as a whole value, to keep the review readable and the recursion bounded. */
const MAX_DEPTH = 8;

/** A string holding a JSON object or array (settings are stored that way) is opened up; any other value is left alone. */
function open(value: unknown): unknown {
  if (typeof value !== 'string') return value;
  const text = value.trim();
  if (!(text.startsWith('{') || text.startsWith('['))) return value;
  try {
    const parsed: unknown = JSON.parse(text);
    return typeof parsed === 'object' && parsed !== null ? parsed : value;
  } catch {
    return value;
  }
}

function isContainer(value: unknown): value is Record<string, unknown> | unknown[] {
  return typeof value === 'object' && value !== null;
}

function join(path: string, key: string | number): string {
  if (typeof key === 'number') return `${path}[${key}]`;
  return path ? `${path}.${key}` : key;
}

function same(a: unknown, b: unknown): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

function walk(path: string, before: unknown, after: unknown, depth: number, out: DeepDiffRow[]): void {
  const b = open(before);
  const a = open(after);
  if (same(b, a)) return;
  if (b === undefined) { out.push({ path, kind: 'ADDED', before: undefined, after: a }); return; }
  if (a === undefined) { out.push({ path, kind: 'REMOVED', before: b, after: undefined }); return; }
  if (depth < MAX_DEPTH && isContainer(b) && isContainer(a) && Array.isArray(b) === Array.isArray(a)) {
    const keys: Array<string | number> = Array.isArray(b)
      ? Array.from({ length: Math.max(b.length, (a as unknown[]).length) }, (_, i) => i)
      : Array.from(new Set([...Object.keys(b), ...Object.keys(a)]));
    for (const key of keys) {
      walk(join(path, key), (b as Record<string | number, unknown>)[key], (a as Record<string | number, unknown>)[key], depth + 1, out);
    }
    return;
  }
  out.push({ path, kind: 'CHANGED', before: b, after: a });
}

/**
 * Expands the backend's top-level difference into the exact leaves that changed. A setting such as `value` arrives as
 * one big JSON string on each side; this opens both and lists only `tripCount[1].bonus: 200 -> 300`-style rows.
 */
export function deepDiff(entries: ReadonlyArray<ChangeDiffEntry>): DeepDiffRow[] {
  const out: DeepDiffRow[] = [];
  for (const e of entries) {
    walk(e.path, e.change === 'ADDED' ? undefined : e.before, e.change === 'REMOVED' ? undefined : e.after, 0, out);
  }
  return out;
}

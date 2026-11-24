/** The options that contain the typed text (case-insensitive); every option when nothing is typed. */
export function hqSearchableFilter(options: readonly string[], term: string): string[] {
  const needle = term.trim().toLowerCase();
  return needle ? options.filter((option) => option.toLowerCase().includes(needle)) : [...options];
}

/** The listed option equal to the value ignoring case and surrounding spaces, or undefined when it is not in the list. */
export function hqSearchableMatch(options: readonly string[], value: string | null | undefined): string | undefined {
  const wanted = (value ?? '').trim().toLowerCase();
  return wanted ? options.find((option) => option.trim().toLowerCase() === wanted) : undefined;
}

/** Moves a keyboard position by one step inside 0..length-1 without wrapping; -1 means nothing is highlighted yet. */
export function hqSearchableStep(current: number, delta: number, length: number): number {
  if (length <= 0) return -1;
  const next = current < 0 ? (delta > 0 ? 0 : length - 1) : current + delta;
  return Math.min(length - 1, Math.max(0, next));
}

/** A piece of an option's text; `match` marks the part the typed text matched. */
export interface HqSearchableSegment {
  text: string;
  match: boolean;
}

/** Splits an option's text around the first (case-insensitive) occurrence of the typed text, so the match can be highlighted. */
export function hqSearchableSegments(label: string, term: string): HqSearchableSegment[] {
  const needle = term.trim().toLowerCase();
  const at = needle ? label.toLowerCase().indexOf(needle) : -1;
  if (at < 0) return [{ text: label, match: false }];
  const segments: HqSearchableSegment[] = [];
  if (at > 0) segments.push({ text: label.slice(0, at), match: false });
  segments.push({ text: label.slice(at, at + needle.length), match: true });
  if (at + needle.length < label.length) segments.push({ text: label.slice(at + needle.length), match: false });
  return segments;
}

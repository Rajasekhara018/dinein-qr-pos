/**
 * Pure helpers for the sidebar's search box: they decide which menu items match what was typed and where the
 * match is, so the component only orchestrates. Matching ignores case and Latin accents; scripts such as
 * Devanagari or Tamil keep their vowel signs (only the combining-diacritics block is removed).
 */

const LATIN_COMBINING_MARKS = /[̀-ͯ]/g;

/** A piece of a label; `match` pieces are the parts that matched the search. */
export interface TextSegment {
  text: string;
  match: boolean;
}

/**
 * One character folded for matching: lower case with Latin accents removed. Always the same length as the input
 * character, so an index in the folded text is the same index in the original (needed for highlighting).
 */
function foldChar(ch: string): string {
  const folded = ch.normalize('NFD').replace(LATIN_COMBINING_MARKS, '').toLowerCase();
  if (folded.length === ch.length) return folded;
  const lower = ch.toLowerCase();
  return lower.length === ch.length ? lower : ch;
}

/** The text lower-cased and without Latin accents, same length as the original. */
export function foldText(text: string): string {
  let out = '';
  for (const ch of text) out += foldChar(ch);
  return out;
}

/** The words of a search, folded; an empty or blank search has no tokens. */
export function searchTokens(query: string): string[] {
  return foldText(query).split(/\s+/).filter(Boolean);
}

/** Whether every word of the search appears in the item's label or section heading. */
export function matchesTokens(label: string, section: string, tokens: readonly string[]): boolean {
  if (tokens.length === 0) return true;
  const haystack = foldText(`${label} ${section}`);
  return tokens.every((token) => haystack.includes(token));
}

/** The label split into matched and unmatched pieces (one unmatched piece when there is nothing to highlight). */
export function highlightSegments(text: string, tokens: readonly string[]): TextSegment[] {
  if (!text) return [];
  if (tokens.length === 0) return [{ text, match: false }];

  const folded = foldText(text);
  const marked = new Array<boolean>(text.length).fill(false);
  for (const token of tokens) {
    let from = 0;
    for (;;) {
      const at = folded.indexOf(token, from);
      if (at < 0) break;
      for (let i = at; i < at + token.length; i++) marked[i] = true;
      from = at + 1; // overlapping occurrences count too ("ana" twice in "banana")
    }
  }

  const segments: TextSegment[] = [];
  let start = 0;
  for (let i = 1; i <= text.length; i++) {
    if (i === text.length || marked[i] !== marked[start]) {
      segments.push({ text: text.slice(start, i), match: marked[start] });
      start = i;
    }
  }
  return segments;
}

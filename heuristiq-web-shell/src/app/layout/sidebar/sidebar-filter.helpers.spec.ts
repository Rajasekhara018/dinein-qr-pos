import { describe, expect, it } from 'vitest';
import { foldText, highlightSegments, matchesTokens, searchTokens } from './sidebar-filter.helpers';

describe('sidebar filter helpers', () => {
  it('folds case and Latin accents without changing the length', () => {
    expect(foldText('Café Résumé')).toBe('cafe resume');
    expect(foldText('Café').length).toBe('Café'.length);
  });

  it('keeps the vowel signs of Indic scripts so Hindi and Tamil words still match', () => {
    expect(foldText('उपयोगकर्ता')).toBe('उपयोगकर्ता');
    expect(matchesTokens('उपयोगकर्ता', 'पहुँच प्रबंधन', searchTokens('उपयोग'))).toBe(true);
    expect(matchesTokens('பயனர்கள்', 'அணுகல்', searchTokens('பயனர்'))).toBe(true);
  });

  it('splits a search into folded words and ignores blanks', () => {
    expect(searchTokens('  Train   REFUNDS ')).toEqual(['train', 'refunds']);
    expect(searchTokens('   ')).toEqual([]);
  });

  it('matches when every word is in the label or the section, in any order', () => {
    const tokens = searchTokens('refunds train');
    expect(matchesTokens('Refunds', 'Train operations', tokens)).toBe(true);
    expect(matchesTokens('Refunds', 'Wallet', tokens)).toBe(false);
  });

  it('matches everything when there is nothing typed', () => {
    expect(matchesTokens('Anything', 'Any section', [])).toBe(true);
  });

  it('highlights each matched part of a label', () => {
    expect(highlightSegments('Train refunds', searchTokens('refu'))).toEqual([
      { text: 'Train ', match: false },
      { text: 'refu', match: true },
      { text: 'nds', match: false },
    ]);
  });

  it('merges overlapping and repeated matches and finds accented text', () => {
    expect(highlightSegments('Banana', searchTokens('an ana'))).toEqual([
      { text: 'B', match: false },
      { text: 'anana', match: true },
    ]);
    expect(highlightSegments('Café menu', searchTokens('cafe'))).toEqual([
      { text: 'Café', match: true },
      { text: ' menu', match: false },
    ]);
  });

  it('returns the whole label unmarked when nothing is typed or nothing matches, and nothing for empty text', () => {
    expect(highlightSegments('Users', [])).toEqual([{ text: 'Users', match: false }]);
    expect(highlightSegments('Users', searchTokens('zzz'))).toEqual([{ text: 'Users', match: false }]);
    expect(highlightSegments('', searchTokens('a'))).toEqual([]);
  });
});

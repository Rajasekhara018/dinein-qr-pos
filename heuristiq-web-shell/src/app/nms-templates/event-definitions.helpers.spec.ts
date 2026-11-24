import { describe, expect, it } from 'vitest';
import {
  diffEvents, duplicateEventValues, eventSignature, eventsSignature, indexOfSelection, normalizeEvent, normalizeEventAttribute,
  normalizeEventValue,
} from './event-definitions.helpers';
import { NmsEventDef } from './event-attributes.config';

const ev = (value: string, label = value, attributes: string[] = [], id?: string): NmsEventDef => ({ ...(id ? { id } : {}), value, label, attributes });

describe('event definition helpers', () => {
  it('turns labels and typed values into the key templates use', () => {
    expect(normalizeEventValue(' Password reset ')).toBe('PASSWORD_RESET');
    expect(normalizeEventValue('passwordReset')).toBe('PASSWORD_RESET');
    expect(normalizeEventValue('--ride/done 2!')).toBe('RIDE_DONE_2');
    expect(normalizeEventValue('   ')).toBe('');
  });

  it('puts attributes in braces and drops blanks', () => {
    expect(normalizeEventAttribute(' name ')).toBe('{{name}}');
    expect(normalizeEventAttribute('{{ name }}')).toBe('{{name}}');
    expect(normalizeEventAttribute('{{}}')).toBe('');
  });

  it('cleans an event, taking the value from the label when it is blank, and keeps the id', () => {
    expect(normalizeEvent({ id: 'a', value: '', label: ' New event ', attributes: ['x', ' ', '{{y}}'] }))
      .toEqual({ id: 'a', value: 'NEW_EVENT', label: 'New event', attributes: ['{{x}}', '{{y}}'] });
  });

  it('gives equal signatures to events that differ only by id or by tidiness', () => {
    expect(eventSignature(ev('A_B', 'A b', ['{{x}}'], '1'))).toBe(eventSignature(ev('a b', ' A b ', ['x'])));
    expect(eventsSignature([ev('A')])).not.toBe(eventsSignature([ev('A'), ev('B')]));
    expect(eventsSignature([ev('A'), ev('B')])).not.toBe(eventsSignature([ev('B'), ev('A')]));
  });

  describe('diffEvents', () => {
    const loaded = [ev('A', 'A', [], '1'), ev('B', 'B', ['{{x}}'], '2'), ev('C', 'C', [], '3')];

    it('sends nothing when nothing changed, however many events there are', () => {
      expect(diffEvents(loaded.map((e) => ({ ...e })), loaded)).toEqual({ created: [], updated: [], removed: [] });
    });

    it('ignores edits that normalise back to the loaded content', () => {
      const same = [ev('a', ' A ', [], '1'), ev('B', 'B', ['x'], '2'), ev('C', 'C', [], '3')];
      expect(diffEvents(same, loaded).updated).toEqual([]);
    });

    it('updates only the events that changed', () => {
      const edited = [ev('A', 'A', [], '1'), ev('B', 'B renamed', ['{{x}}'], '2'), ev('C', 'C', ['{{new}}'], '3')];
      const changes = diffEvents(edited, loaded);
      expect(changes.updated.map((e) => e.id)).toEqual(['2', '3']);
      expect(changes.created).toEqual([]);
      expect(changes.removed).toEqual([]);
    });

    it('creates events without an id and removes loaded ones that are gone', () => {
      const changes = diffEvents([ev('A', 'A', [], '1'), ev('', 'Brand new')], loaded);
      expect(changes.created).toEqual([{ value: 'BRAND_NEW', label: 'Brand new', attributes: [] }]);
      expect(changes.removed.map((e) => e.id)).toEqual(['2', '3']);
    });

    it('treats a saved event unknown to the baseline as an update', () => {
      expect(diffEvents([ev('Z', 'Z', [], '9')], []).updated.map((e) => e.id)).toEqual(['9']);
    });
  });

  it('lists each duplicated value once, by its cleaned form', () => {
    expect(duplicateEventValues([ev('A'), ev('a'), ev('B'), ev('A '), ev('')])).toEqual(['A']);
    expect(duplicateEventValues([ev('A'), ev('B')])).toEqual([]);
    expect(duplicateEventValues([ev('', 'Same label'), ev('SAME_LABEL')])).toEqual(['SAME_LABEL']);
  });

  describe('indexOfSelection', () => {
    const list = [ev('A', 'A', [], '1'), ev('B', 'B', [], '2'), ev('C', 'C', [], '3')];

    it('finds the event by id even after the list is reordered', () => {
      expect(indexOfSelection([list[2], list[0], list[1]], { id: '2', value: 'B' })).toBe(2);
    });

    it('finds a new event by value once the server has given it an id', () => {
      expect(indexOfSelection(list, { value: 'c' })).toBe(2);
    });

    it('falls back to the first event, or -1 for an empty list', () => {
      expect(indexOfSelection(list, { id: 'gone', value: 'GONE' })).toBe(0);
      expect(indexOfSelection(list, null)).toBe(0);
      expect(indexOfSelection([], { id: '1', value: 'A' })).toBe(-1);
    });
  });
});

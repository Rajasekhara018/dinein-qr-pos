import { NmsEventDef } from './event-attributes.config';

/** What a save has to do to bring the server in line with the screen. */
export interface EventChanges {
  /** Events with no id yet. */
  created: NmsEventDef[];
  /** Saved events whose label, value or attributes differ from what was loaded. */
  updated: NmsEventDef[];
  /** Events that were loaded but are no longer on the screen. */
  removed: NmsEventDef[];
}

/** The event the user is working on, found again after the list is replaced (a saved event by id, a new one by value). */
export interface EventSelection {
  id?: string;
  value: string;
}

/** The key templates use: upper case words joined by underscores ("Password reset" and "passwordReset" both give PASSWORD_RESET). */
export function normalizeEventValue(value: string): string {
  return value
    .trim()
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .replace(/[^a-zA-Z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .toUpperCase();
}

/** A placeholder in {{braces}} form; blank when nothing usable was typed. */
export function normalizeEventAttribute(attribute: string): string {
  const cleaned = attribute.trim().replace(/^\{\{\s*/, '').replace(/\s*\}\}$/, '').trim();
  return cleaned ? `{{${cleaned}}}` : '';
}

/** The event as it is sent: trimmed label, a clean value (taken from the label when blank), clean attributes; the id is kept. */
export function normalizeEvent(event: NmsEventDef): NmsEventDef {
  const label = (event.label ?? '').trim();
  return {
    ...event,
    label,
    value: normalizeEventValue(event.value || label),
    attributes: (event.attributes ?? []).map(normalizeEventAttribute).filter(Boolean),
  };
}

/** What makes two events the same for saving: the id is not part of it. */
export function eventSignature(event: NmsEventDef): string {
  const { label, value, attributes } = normalizeEvent(event);
  return JSON.stringify({ label, value, attributes });
}

/** A fingerprint of the whole list, in order, for the "unsaved changes" check. */
export function eventsSignature(events: readonly NmsEventDef[]): string {
  return JSON.stringify(events.map((event) => JSON.parse(eventSignature(event))));
}

/**
 * Only what changed: new events, saved events that differ from the loaded copy, and loaded events that were removed.
 * An untouched event is never sent, so one edit is one request, not one per event.
 */
export function diffEvents(current: readonly NmsEventDef[], baseline: readonly NmsEventDef[]): EventChanges {
  const loaded = new Map(baseline.filter((event) => event.id).map((event) => [event.id as string, eventSignature(event)]));
  const kept = new Set<string>();
  const created: NmsEventDef[] = [];
  const updated: NmsEventDef[] = [];
  for (const event of current) {
    const clean = normalizeEvent(event);
    if (!clean.id) {
      created.push(clean);
      continue;
    }
    kept.add(clean.id);
    if (loaded.get(clean.id) !== eventSignature(clean)) {
      updated.push(clean);
    }
  }
  const removed = baseline.filter((event) => event.id && !kept.has(event.id));
  return { created, updated, removed };
}

/** The values used by more than one event, once each. */
export function duplicateEventValues(events: readonly NmsEventDef[]): string[] {
  const seen = new Set<string>();
  const duplicates = new Set<string>();
  for (const event of events) {
    const value = normalizeEventValue(event.value || event.label || '');
    if (!value) {
      continue;
    }
    if (seen.has(value)) {
      duplicates.add(value);
    }
    seen.add(value);
  }
  return [...duplicates];
}

/** Where the selected event is in a new list: by id, else by value, else the first one; -1 for an empty list. */
export function indexOfSelection(events: readonly NmsEventDef[], selection: EventSelection | null): number {
  if (events.length === 0) {
    return -1;
  }
  if (selection) {
    const byId = selection.id ? events.findIndex((event) => event.id === selection.id) : -1;
    if (byId >= 0) {
      return byId;
    }
    const byValue = events.findIndex((event) => normalizeEventValue(event.value) === normalizeEventValue(selection.value));
    if (byValue >= 0) {
      return byValue;
    }
  }
  return 0;
}

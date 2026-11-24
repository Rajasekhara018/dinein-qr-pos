import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import {
  HQ_REFERENCE_EMPTY,
  HQ_REFERENCE_FETCHER,
  HqReferenceList,
  HqReferenceOption,
  HqReferencePage,
} from './hq-reference-options.model';

interface Entry {
  category: string;
  parentValue: string | null;
  subject: BehaviorSubject<HqReferenceList>;
  /** The same observable every time, so `| async` in a template does not resubscribe on each check. */
  list$: Observable<HqReferenceList>;
}

/** The active values of a page, trimmed, without repeats (compared ignoring case) and in the order Ops set. */
export function hqReferenceValues(page: HqReferencePage | null | undefined): string[] {
  const seen = new Set<string>();
  const values: string[] = [];
  for (const option of page?.content ?? ([] as HqReferenceOption[])) {
    const value = (option.value ?? '').trim();
    const key = value.toLowerCase();
    if (option.active !== false && value && !seen.has(key)) {
      seen.add(key);
      values.push(value);
    }
  }
  return values;
}

/**
 * The one place every screen reads its reference lists (religion, community, country, state, city, ...) from, so the sign-up
 * form, the search filters and the ops screens show identical lists. Each list is read once per session and kept (a list
 * under a parent, such as the states of one country, is its own entry), so reopening a page does not ask again. While a list
 * is being read its status is `loading`; if the read fails it is `failed` and is not kept, so `retry` asks again. A screen
 * binds `list$(...) | async` to a control: a loading list is a disabled control with a spinner, a failed one a free-text box.
 */
@Injectable({ providedIn: 'root' })
export class HqReferenceOptionsService {
  private readonly fetcher = inject(HQ_REFERENCE_FETCHER);
  private readonly entries = new Map<string, Entry>();

  /** The list of a category (under a parent for a state or city). The same observable is returned for the same list. */
  list$(category: string, parentValue?: string | null): Observable<HqReferenceList> {
    return this.entry(category, parentValue ?? null).list$;
  }

  /** The list right now (`loading` until the read finished). */
  snapshot(category: string, parentValue?: string | null): HqReferenceList {
    return this.entry(category, parentValue ?? null).subject.value;
  }

  /** Asks again for a list that failed; a list that loaded or is loading is left alone. */
  retry(category: string, parentValue?: string | null): void {
    const entry = this.entry(category, parentValue ?? null);
    if (entry.subject.value.status === 'failed') this.load(entry);
  }

  /** The empty, nothing-to-wait-for list (for a child whose parent is not chosen). */
  get empty(): HqReferenceList {
    return HQ_REFERENCE_EMPTY;
  }

  private entry(category: string, parentValue: string | null): Entry {
    const key = `${category}|${(parentValue ?? '').trim().toLowerCase()}`;
    let entry = this.entries.get(key);
    if (!entry) {
      const subject = new BehaviorSubject<HqReferenceList>({ status: 'loading', values: [] });
      entry = { category, parentValue: parentValue?.trim() || null, subject, list$: subject.asObservable() };
      this.entries.set(key, entry);
      this.load(entry);
    }
    return entry;
  }

  private load(entry: Entry): void {
    entry.subject.next({ status: 'loading', values: [] });
    this.fetcher(entry.category, entry.parentValue).subscribe({
      next: (page) => entry.subject.next({ status: 'loaded', values: hqReferenceValues(page) }),
      error: () => entry.subject.next({ status: 'failed', values: [] }),
    });
  }
}

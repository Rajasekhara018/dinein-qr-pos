import { Observable, of } from 'rxjs';
import { HQ_REFERENCE_EMPTY, HqReferenceList } from './hq-reference-options.model';
import { HqReferenceOptionsService } from './hq-reference-options.service';

/**
 * Ties the fields of one form (the sign-up form, the search filters, an ops screen) to the reference lists: which field reads
 * which list, which field waits for another (the state for the country, the city for the state) and how to read that other
 * field's current answer. It owns no state of its own; the lists and their loading / failed status live in
 * HqReferenceOptionsService, so every screen shows the same lists and asks for each only once per session.
 */
export class HqReferenceBinder {
  /** The nothing-to-wait-for list of a child whose parent is not chosen (one instance, so `| async` does not resubscribe). */
  private readonly noList$: Observable<HqReferenceList> = of(HQ_REFERENCE_EMPTY);

  constructor(
    private readonly service: HqReferenceOptionsService,
    /** Field key -> reference category (RELIGION, COUNTRY, STATE, ...). */
    private readonly categoryByKey: Readonly<Record<string, string>>,
    /** Child field key -> the field whose answer it is listed under (state -> country, city -> state). */
    private readonly parentByKey: Readonly<Record<string, string>>,
    /** The current answer of a field of the form. */
    private readonly answerOf: (key: string) => unknown,
  ) {}

  /** True when the field is drawn as a drop-down over a reference list. */
  has(key: string): boolean {
    return !!this.categoryByKey[key];
  }

  /** The field a child waits for, or undefined for a field with no parent. */
  parentKey(key: string): string | undefined {
    return this.parentByKey[key];
  }

  /** The parent's current answer (trimmed), or null when the field has no parent or the parent is not chosen. */
  parentValue(key: string): string | null {
    const parentKey = this.parentByKey[key];
    const value = parentKey ? String(this.answerOf(parentKey) ?? '').trim() : '';
    return value || null;
  }

  /** The list of a field: under the chosen parent for a child, and the empty list while that parent is not chosen. */
  list$(key: string): Observable<HqReferenceList> {
    const parent = this.parentValue(key);
    if (this.parentByKey[key] && !parent) return this.noList$;
    return this.service.list$(this.categoryByKey[key], parent);
  }

  /** Asks again for the list of a field whose read failed. */
  retry(key: string): void {
    this.service.retry(this.categoryByKey[key], this.parentValue(key));
  }
}

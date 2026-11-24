import { TranslateService } from '@ngx-translate/core';
import { Observable, Subscription } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { HqCommonTableColumn } from 'hq-shared-lib';

/**
 * A column described with a translation KEY for its heading (the list screens keep these in their sibling
 * `*.constants.ts`); {@link translateColumns} turns the specs into the already-translated columns hq-common-table needs.
 */
export interface TableColumnSpec<T extends object = Record<string, unknown>> {
  /** Dot-path into the row, or the key of a `hqCell` template. */
  key: string;
  /** Translation key of the heading; empty for a column without a heading (an icon or action column). */
  labelKey: string;
  render?: (row: T) => string;
  sortable?: boolean;
  tooltip?: boolean;
  className?: string;
}

/** The specs with their headings translated now. */
export function translateColumns<T extends object>(
  specs: readonly TableColumnSpec<T>[],
  translate: TranslateService,
): HqCommonTableColumn<T>[] {
  return specs.map((spec) => ({
    key: spec.key,
    label: spec.labelKey ? translate.instant(spec.labelKey) : '',
    render: spec.render,
    sortable: spec.sortable,
    tooltip: spec.tooltip,
    className: spec.className,
  }));
}

/**
 * Same as {@link watchColumns} for a screen that already ends its streams with a `destroy$` subject: the language
 * subscription stops when `until$` emits, so the screen has nothing more to clean up.
 */
export function watchColumnsUntil<T extends object>(
  translate: TranslateService,
  specs: readonly TableColumnSpec<T>[],
  assign: (columns: HqCommonTableColumn<T>[]) => void,
  until$: Observable<unknown>,
): void {
  assign(translateColumns(specs, translate));
  translate.onLangChange.pipe(takeUntil(until$)).subscribe(() => assign(translateColumns(specs, translate)));
}

/**
 * Builds the columns now and again on every language switch, so the headings follow the language like a `| translate`
 * pipe would. Returns the subscription: the screen unsubscribes it in `ngOnDestroy`.
 */
export function watchColumns<T extends object>(
  translate: TranslateService,
  specs: readonly TableColumnSpec<T>[],
  assign: (columns: HqCommonTableColumn<T>[]) => void,
): Subscription {
  assign(translateColumns(specs, translate));
  return translate.onLangChange.subscribe(() => assign(translateColumns(specs, translate)));
}

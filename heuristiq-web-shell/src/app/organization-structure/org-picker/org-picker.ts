import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { Subject, Subscription, debounceTime, distinctUntilChanged, switchMap, of, catchError, Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { FieldKind, PICKER_MAX_RESULTS, PICKER_SEARCH_DEBOUNCE_MS } from '../org-structure.constants';
import { matchesQuery, mergePickerItems } from '../org-structure.helpers';
import { PickerItem } from '../org-structure.models';
import { OrgStructureService } from '../org-structure.service';

/**
 * Type-to-search selector for units, ladders, positions and employees. Live records come from the
 * server (capped, never the whole list); records created earlier in the same draft are passed in as
 * `draftItems` because the server cannot list them until the draft is published.
 */
@Component({
  selector: 'app-org-picker',
  standalone: false,
  templateUrl: './org-picker.html',
  styleUrl: './org-picker.scss',
})
export class OrgPicker implements OnChanges, OnDestroy {
  @Input({ required: true }) kind!: Exclude<FieldKind, 'text' | 'number' | 'boolean'>;
  @Input() placeholder = '';
  @Input() draftItems: PickerItem[] = [];
  /** For kind 'position': the ladder whose positions are offered. */
  @Input() ladderId: string | null = null;
  @Input() selectedLabel = '';
  @Output() picked = new EventEmitter<PickerItem | null>();

  query = '';
  open = false;
  isLoading = false;
  items: PickerItem[] = [];

  private readonly queries$ = new Subject<string>();
  private readonly subscription: Subscription;

  constructor(private api: OrgStructureService, private cdr: ChangeDetectorRef) {
    this.subscription = this.queries$
      .pipe(
        debounceTime(PICKER_SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        switchMap((query) => this.search(query).pipe(catchError(() => of([] as PickerItem[])))),
      )
      .subscribe((live) => {
        this.items = mergePickerItems(this.localDraftMatches(), live).slice(0, PICKER_MAX_RESULTS);
        this.isLoading = false;
        this.cdr.detectChanges();
      });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedLabel']) {
      this.query = this.selectedLabel;
    }
    if (changes['ladderId'] && !changes['ladderId'].firstChange) {
      this.items = [];
    }
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  onFocus(): void {
    this.open = true;
    this.refresh(this.query === this.selectedLabel ? '' : this.query);
  }

  onInput(value: string): void {
    this.query = value;
    this.open = true;
    this.picked.emit(null); // typing invalidates the earlier choice until one is picked again
    this.refresh(value);
  }

  choose(item: PickerItem): void {
    this.query = item.label;
    this.open = false;
    this.picked.emit(item);
  }

  /** Closing is deferred so a click on a result is handled before the list disappears. */
  onBlur(): void {
    setTimeout(() => { this.open = false; this.cdr.detectChanges(); }, 150);
  }

  private refresh(query: string): void {
    this.isLoading = true;
    this.items = this.localDraftMatches(query);
    this.queries$.next(query + '\u0000' + (this.ladderId ?? ''));
  }

  private localDraftMatches(query = this.query): PickerItem[] {
    return this.draftItems.filter((item) => matchesQuery(item.label, query));
  }

  private search(composite: string): Observable<PickerItem[]> {
    const query = composite.split('\u0000')[0];
    switch (this.kind) {
      case 'unit':
        return this.api.searchUnits(query, PICKER_MAX_RESULTS).pipe(
          map((units) => units.map((u) => ({ id: u.id, label: `${u.name} (${u.code})`, ladderId: u.ladderId }))));
      case 'employee':
        return this.api.searchEmployees(query, PICKER_MAX_RESULTS).pipe(
          map((people) => people.map((e) => ({ id: e.id, label: `${e.fullName} (${e.employeeCode})` }))));
      case 'ladder':
        return this.api.ladders().pipe(
          map((ladders) => ladders.filter((l) => matchesQuery(l.name, query)).map((l) => ({ id: l.id, label: l.name }))));
      case 'position':
        return this.ladderId
          ? this.api.positions(this.ladderId).pipe(
            map((positions) => positions.filter((p) => matchesQuery(p.name, query)).map((p) => ({ id: p.id, label: p.name, ladderId: p.ladderId }))))
          : of([]);
    }
  }
}

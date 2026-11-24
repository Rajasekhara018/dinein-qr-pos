import { ChangeDetectorRef, Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, Subscription } from 'rxjs';
import { FranchiseApi } from './franchise-api';
import { Location, LocationType, PageResult, PlaceRef } from './franchise.models';
import { LOCATION_TYPE_LABEL_KEYS, PICKER_PAGE_SIZE } from './franchise-locations.constants';
import { deepestSelectable } from './franchise-locations.helpers';
import { labelKey } from './franchise-earnings.helpers';

/** One drop-down of the cascade: the places to choose from at this depth and the one chosen. */
export interface PickerLevel {
  options: Location[];
  total: number;
  selectedId: string;
  q: string;
}

/**
 * A cascading place picker: the states of a country, then (once one is chosen) the places directly under it, and so on as deep
 * as the reference goes. Only usable places (active, with active parents) are offered. The value is the deepest chosen place
 * whose type is in {@link selectableTypes} (any type when null), so a form that needs a state or a district as the parent is
 * never given a village.
 */
@Component({
  selector: 'app-location-picker',
  standalone: false,
  templateUrl: './location-picker.html',
})
export class LocationPicker implements OnChanges, OnDestroy {
  @Input() countryCode = '';
  /** The path to show as already chosen (top first), e.g. the chain of the plan being changed. */
  @Input() chain: readonly PlaceRef[] = [];
  @Input() selectableTypes: readonly LocationType[] | null = null;
  @Input() disabled = false;
  /** Read the franchise form's own place list (privileges FRANCHISE_PLACE_OPTION_*) instead of the Locations screen's. */
  @Input() franchiseScoped = false;
  @Output() selected = new EventEmitter<Location | null>();

  levels: PickerLevel[] = [];
  failed = false;
  loading = false;
  readonly pageSize = PICKER_PAGE_SIZE;

  private chosen: (Location | null)[] = [];
  private lastEmitted: Location | null = null;
  private request?: Subscription;

  constructor(private api: FranchiseApi, private translate: TranslateService, private cdr: ChangeDetectorRef) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['countryCode'] || changes['chain']) {
      this.reset();
    }
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
  }

  typeLabel(type: string): string {
    const key = labelKey(LOCATION_TYPE_LABEL_KEYS, type);
    return key ? this.translate.instant(key) : type;
  }

  /** A place was chosen (or cleared) at depth {@code index}: everything deeper starts again and the places under it are loaded. */
  choose(index: number, id: string): void {
    this.levels.length = index + 1;
    this.chosen.length = index;
    const level = this.levels[index];
    const place = level.options.find((option) => option.id === id) ?? null;
    level.selectedId = place ? place.id : '';
    if (!place) {
      this.emit();
      return;
    }
    this.chosen[index] = place;
    this.emit();
    this.loadChildren(index, place, () => this.finish());
  }

  /** The type-ahead of a long level: asks again for the places matching the text, and clears what was chosen there. */
  search(index: number, text: string): void {
    this.levels.length = index + 1;
    this.chosen.length = index;
    this.levels[index].selectedId = '';
    this.levels[index].q = text.trim();
    this.emit();
    const parent = index === 0 ? null : this.chosen[index - 1];
    this.fetch(parent, this.levels[index].q).subscribe({
      next: (page) => {
        this.levels[index].options = page.items ?? [];
        this.levels[index].total = page.totalElements ?? 0;
        this.cdr.detectChanges();
      },
      error: () => this.fail(),
    });
  }

  onSearch(index: number, event: Event): void {
    this.search(index, (event.target as HTMLInputElement).value);
  }

  private reset(): void {
    this.request?.unsubscribe();
    this.levels = [];
    this.chosen = [];
    this.failed = false;
    this.emit();
    if (!this.countryCode) {
      return;
    }
    this.loading = true;
    this.request = this.fetch(null, '').subscribe({
      next: (page) => {
        this.levels = [{ options: page.items ?? [], total: page.totalElements ?? 0, selectedId: '', q: '' }];
        this.loading = false;
        this.applyChain(0);
      },
      error: () => this.fail(),
    });
  }

  /** Selects the given path level by level (loading each level's children), then reports the value. */
  private applyChain(index: number): void {
    const step = this.chain[index];
    const found = step ? this.levels[index]?.options.find((option) => option.id === step.id) : undefined;
    if (!found) {
      this.finish();
      return;
    }
    this.levels[index].selectedId = found.id;
    this.chosen[index] = found;
    this.loadChildren(index, found, () => this.applyChain(index + 1));
  }

  private loadChildren(index: number, place: Location, next: () => void): void {
    this.request?.unsubscribe();
    this.loading = true;
    this.request = this.fetch(place, '').subscribe({
      next: (page) => {
        this.loading = false;
        if (this.chosen[index]?.id !== place.id) {
          return;
        }
        if ((page.items ?? []).length > 0) {
          this.levels.length = index + 1;
          this.levels.push({ options: page.items, total: page.totalElements ?? page.items.length, selectedId: '', q: '' });
        }
        next();
      },
      error: () => this.fail(),
    });
  }

  private fetch(parent: Location | null, q: string): Observable<PageResult<Location>> {
    if (this.franchiseScoped) {
      return parent
        ? this.api.placeOptionChildren(parent.id, { q, size: PICKER_PAGE_SIZE })
        : this.api.placeOptions({ countryCode: this.countryCode, type: 'STATE', q, size: PICKER_PAGE_SIZE });
    }
    return parent
      ? this.api.locationChildren(parent.id, { q, size: PICKER_PAGE_SIZE })
      : this.api.locations({ countryCode: this.countryCode, type: 'STATE', usable: true, q, size: PICKER_PAGE_SIZE });
  }

  private finish(): void {
    this.emit();
    this.cdr.detectChanges();
  }

  private fail(): void {
    this.failed = true;
    this.loading = false;
    this.cdr.detectChanges();
  }

  private emit(): void {
    const value = deepestSelectable(this.chosen, this.selectableTypes);
    if ((value?.id ?? null) !== (this.lastEmitted?.id ?? null)) {
      this.lastEmitted = value;
      this.selected.emit(value);
    }
  }
}

import {
  AfterContentInit, ChangeDetectorRef, Component, ContentChildren, ElementRef, EventEmitter, forwardRef, inject, Input,
  OnDestroy, Output, QueryList, ViewChild, booleanAttribute,
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Subscription } from 'rxjs';
import {
  HQ_TABS_DEFAULT_APPEARANCE, HQ_TABS_DEFAULT_SIZE, HqTabLink, HqTabsAppearance, HqTabsSize,
} from './hq-tabs.constants';

/**
 * One tab of a `<hq-tabs>` strip. A tab either selects a value (`value`) or, with `link`, navigates and is active
 * while its route is (routerLinkActive), in which case `value` is ignored. The host gets `hq-tab--active` while
 * active so a screen can style around it.
 */
@Component({
  selector: 'hq-tab',
  standalone: false,
  host: { class: 'hq-tab-host', '[class.hq-tab--active]': 'active', '[class.hq-tab--disabled]': 'isDisabled' },
  template: `
    <ng-template #content>
      @if (icon) {<i class="bi hq-tab__icon" [ngClass]="icon" aria-hidden="true"></i>}
      <span class="hq-tab__label">@if (label) {{{ label }}} @else {<ng-content></ng-content>}</span>
      @if (hasBadge) {<span class="hq-tab__badge">{{ badge }}</span>}
    </ng-template>
    @if (link !== null && link !== undefined) {
      <a #el class="hq-tab" role="tab" [class.hq-tab--active]="active" [routerLink]="$any(link)"
         routerLinkActive="hq-tab--route-active" [routerLinkActiveOptions]="{ exact: linkExact }"
         (isActiveChange)="onLinkActive($event)"
         [attr.aria-selected]="active" [attr.aria-disabled]="isDisabled || null" [attr.tabindex]="tabIndex"
         (click)="onLinkClick($event)">
        <ng-container [ngTemplateOutlet]="content"></ng-container>
      </a>
    } @else {
      <button #el type="button" class="hq-tab" role="tab" [class.hq-tab--active]="active"
              [attr.aria-selected]="active" [disabled]="isDisabled" [attr.tabindex]="tabIndex" (click)="select()">
        <ng-container [ngTemplateOutlet]="content"></ng-container>
      </button>
    }
  `,
  styleUrl: './hq-tab.scss',
})
export class HqTab {
  private readonly cdr = inject(ChangeDetectorRef);
  /** Set by the parent strip. */
  group: HqTabs | null = null;

  @Input() value: unknown = null;
  /** Already-translated text; empty shows the projected content. */
  @Input() label = '';
  /** Optional bootstrap-icons class, e.g. `bi-list`. */
  @Input() icon = '';
  /** A small count bubble; hidden when null or ''. */
  @Input() badge: number | string | null = null;
  @Input({ transform: booleanAttribute }) disabled = false;
  /** Makes the tab a router link; it is then active while its route is. */
  @Input() link: HqTabLink = null;
  /** Route must match exactly to count as active. */
  @Input({ transform: booleanAttribute }) linkExact = false;

  @ViewChild('el') private el?: ElementRef<HTMLElement>;

  private linkActive = false;

  get hasBadge(): boolean {
    return this.badge !== null && this.badge !== undefined && this.badge !== '';
  }

  get isLink(): boolean {
    return this.link !== null && this.link !== undefined;
  }

  get isDisabled(): boolean {
    return this.disabled || !!this.group?.disabled;
  }

  get active(): boolean {
    return this.isLink ? this.linkActive : !!this.group && this.group.isSelected(this.value);
  }

  get tabIndex(): number {
    return this.group?.focusTarget() === this ? 0 : -1;
  }

  select(): void {
    if (this.isDisabled || this.isLink) return;
    this.group?.choose(this.value);
  }

  onLinkClick(event: Event): void {
    if (this.isDisabled) event.preventDefault();
  }

  onLinkActive(isActive: boolean): void {
    // routerLinkActive reports during its own check; apply it on the next tick so the host class does not change mid-check.
    queueMicrotask(() => {
      this.linkActive = isActive;
      this.group?.refresh();
      this.cdr.markForCheck();
    });
  }

  focus(): void {
    this.el?.nativeElement.focus();
  }

  /** Whether this tab's element has the keyboard focus. */
  hasFocus(): boolean {
    return !!this.el && this.el.nativeElement === document.activeElement;
  }
}

/**
 * A tab strip (a form control holding the chosen tab's value; also `[value]` + `(valueChange)`). It only draws the
 * strip; the panels stay in the screen (`@if (tab === 'x')`). Arrow keys, Home and End move between tabs (mirrored in
 * right-to-left layouts), Enter and Space select.
 *
 *   <hq-tabs [(ngModel)]="tab" appearance="segmented" [ariaLabel]="'orders.views' | translate">
 *     <hq-tab value="open" [label]="'orders.open' | translate" [badge]="openCount"></hq-tab>
 *     <hq-tab value="closed" icon="bi-archive">{{ 'orders.closed' | translate }}</hq-tab>
 *   </hq-tabs>
 *   <hq-tabs><hq-tab link="/orders/open" label="Open"></hq-tab><hq-tab link="/orders/all" linkExact label="All"></hq-tab></hq-tabs>
 */
@Component({
  selector: 'hq-tabs',
  standalone: false,
  host: {
    role: 'tablist',
    '[attr.aria-label]': 'ariaLabel || null',
    '[attr.aria-disabled]': 'disabled || null',
    class: 'hq-tabs',
    '[class.hq-tabs--tabs]': "appearance === 'tabs'",
    '[class.hq-tabs--segmented]': "appearance === 'segmented'",
    '[class.hq-tabs--pills]': "appearance === 'pills'",
    '[class.hq-tabs--sm]': "size === 'sm'",
    '[class.hq-tabs--block]': 'block',
    '(keydown)': 'onKey($event)',
  },
  template: `<ng-content></ng-content>`,
  styleUrl: './hq-tabs.scss',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => HqTabs), multi: true }],
})
export class HqTabs implements ControlValueAccessor, AfterContentInit, OnDestroy {
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly cdr = inject(ChangeDetectorRef);
  private tabsSub?: Subscription;

  @Input() value: unknown = null;
  @Input() appearance: HqTabsAppearance = HQ_TABS_DEFAULT_APPEARANCE;
  @Input() size: HqTabsSize = HQ_TABS_DEFAULT_SIZE;
  @Input() ariaLabel = '';
  @Input({ transform: booleanAttribute }) disabled = false;
  /** Stretch the tabs to fill the strip. */
  @Input({ transform: booleanAttribute }) block = false;
  @Output() valueChange = new EventEmitter<unknown>();

  @ContentChildren(HqTab) private tabs?: QueryList<HqTab>;

  private onChange: (value: unknown) => void = () => {};
  private onTouched: () => void = () => {};

  ngAfterContentInit(): void {
    this.attach();
    this.tabsSub = this.tabs?.changes.subscribe(() => this.attach());
  }

  ngOnDestroy(): void {
    this.tabsSub?.unsubscribe();
  }

  isSelected(value: unknown): boolean {
    return value === this.value;
  }

  choose(value: unknown): void {
    if (this.disabled) return;
    this.onTouched();
    if (value === this.value) return;
    this.value = value;
    this.onChange(value);
    this.valueChange.emit(value);
    this.refresh();
  }

  /** The tab that takes part in the tab order: the active one, else the first enabled one. */
  focusTarget(): HqTab | null {
    const tabs = this.tabs?.toArray() ?? [];
    return tabs.find((t) => t.active && !t.isDisabled) ?? tabs.find((t) => !t.isDisabled) ?? null;
  }

  refresh(): void {
    this.cdr.markForCheck();
  }

  onKey(event: KeyboardEvent): void {
    const tabs = (this.tabs?.toArray() ?? []).filter((t) => !t.isDisabled);
    if (!tabs.length) return;
    const focused = tabs.findIndex((t) => t.hasFocus());
    const rtl = getComputedStyle(this.host.nativeElement).direction === 'rtl';
    let next = -1;
    switch (event.key) {
      case 'ArrowRight': next = focused + (rtl ? -1 : 1); break;
      case 'ArrowLeft': next = focused + (rtl ? 1 : -1); break;
      case 'Home': next = 0; break;
      case 'End': next = tabs.length - 1; break;
      case 'Enter':
      case ' ':
        if (focused >= 0 && !tabs[focused].isLink) {
          event.preventDefault();
          tabs[focused].select();
        }
        return;
      default:
        return;
    }
    event.preventDefault();
    tabs[(next + tabs.length) % tabs.length].focus();
  }

  // ---- ControlValueAccessor ----
  writeValue(value: unknown): void {
    this.value = value;
    this.refresh();
  }
  registerOnChange(fn: (value: unknown) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    this.refresh();
  }

  private attach(): void {
    this.tabs?.forEach((t) => (t.group = this));
    this.refresh();
  }
}

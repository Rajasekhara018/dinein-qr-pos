import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import {
  Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, TemplateRef, ViewChild,
  ViewContainerRef, booleanAttribute, inject,
} from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Subscription } from 'rxjs';
import { HQ_UI_STATE_DEFAULT_TEXTS, HQ_UI_STATE_TEXTS, HqUiStateTexts, hqCloseText } from '../hq-view-states/hq-ui-state.texts';
import {
  HQ_DRAWER_BACKDROP_CLASS, HQ_DRAWER_DEFAULT_POSITION, HQ_DRAWER_PANE_CLASS, HQ_DRAWER_WIDTHS,
  HqDrawerPosition, HqDrawerSize,
} from './hq-drawer.constants';

let nextDrawerId = 0;

/**
 * Side panel for detail views and edit forms, driven by the screen's own flag (the shared replacement for the apps'
 * `app-side-drawer`). It slides in from the end edge (right in LTR, left in RTL) above a backdrop, keeps focus inside
 * while open, returns focus to where it was on close and locks page scroll. The close button, the backdrop and Escape
 * emit `closed` (and `openChange(false)`); the screen then sets `open` to false.
 *
 * Slots: the body is the projected content; buttons for the header go in an element marked `hqDrawerActions`;
 * footer buttons in an element marked `hqDrawerFooter` (the old `drawer-footer` attribute works too).
 *
 *   <hq-drawer [open]="!!selected" [title]="'drivers.detail' | translate" (closed)="selected = null">
 *     <hq-key-value-list [items]="rows"></hq-key-value-list>
 *     <div hqDrawerFooter>
 *       <button hqButton="secondary" (click)="selected = null">{{ 'common.close' | translate }}</button>
 *     </div>
 *   </hq-drawer>
 *
 * `width` (px) wins over `size` when both are set; without either the drawer is 480px wide (`size="md"`).
 */
@Component({
  selector: 'hq-drawer',
  standalone: false,
  template: `
    <ng-template #panel>
      <aside class="hq-drawer" [class.hq-drawer--start]="position === 'start'" role="dialog" aria-modal="true"
             [style.inline-size.px]="effectiveWidth"
             [attr.aria-labelledby]="title ? titleId : null" [attr.aria-label]="!title ? (ariaLabel || null) : null"
             cdkTrapFocus [cdkTrapFocusAutoCapture]="true">
        <header class="hq-drawer__header">
          <div class="hq-drawer__heading">
            <h2 class="hq-drawer__title" [id]="titleId">{{ title }}</h2>
            @if (subtitle) {
              <p class="hq-drawer__subtitle">{{ subtitle }}</p>
            }
          </div>
          <div class="hq-drawer__actions"><ng-content select="[hqDrawerActions]"></ng-content></div>
          @if (showClose) {
            <button type="button" class="hq-drawer__close" [attr.aria-label]="closeText" [title]="closeText" (click)="requestClose()">
              <i class="bi bi-x-lg" aria-hidden="true"></i>
            </button>
          }
        </header>
        <div class="hq-drawer__body"><ng-content></ng-content></div>
        <footer class="hq-drawer__footer"><ng-content select="[hqDrawerFooter], [drawer-footer]"></ng-content></footer>
      </aside>
    </ng-template>
  `,
  styleUrl: './hq-drawer.scss',
})
export class HqDrawer implements OnChanges, OnDestroy {
  private readonly overlay = inject(Overlay);
  private readonly vcr = inject(ViewContainerRef);
  private readonly document = inject(DOCUMENT);
  private readonly texts: HqUiStateTexts = inject(HQ_UI_STATE_TEXTS, { optional: true }) ?? HQ_UI_STATE_DEFAULT_TEXTS;

  @Input({ transform: booleanAttribute }) open = false;
  /** Already-translated heading. */
  @Input() title = '';
  /** Already-translated line under the heading. */
  @Input() subtitle = '';
  /** Width in px; wins over `size`. Kept for the apps' old `[width]` binding. */
  @Input() width: number | null = null;
  /** Preset width (sm 360, md 480, lg 640, xl 860 px) used when `width` is not set. */
  @Input() size: HqDrawerSize | null = null;
  /** Edge it slides from: `end` (default; right in LTR) or `start`. */
  @Input() position: HqDrawerPosition = HQ_DRAWER_DEFAULT_POSITION;
  @Input({ transform: booleanAttribute }) closeOnBackdrop = true;
  @Input({ transform: booleanAttribute }) closeOnEscape = true;
  @Input({ transform: booleanAttribute }) showClose = true;
  /** Label of the close button; empty uses the app's close text. */
  @Input() closeLabel = '';
  /** Accessible name when there is no title. */
  @Input() ariaLabel = '';

  /** Close button, backdrop or Escape asked to close; the screen sets `open` to false. */
  @Output() closed = new EventEmitter<void>();
  @Output() openChange = new EventEmitter<boolean>();

  @ViewChild('panel', { static: true }) private panel!: TemplateRef<unknown>;

  readonly titleId = `hq-drawer-title-${nextDrawerId++}`;
  private ref: OverlayRef | null = null;
  private subs = new Subscription();

  get closeText(): string {
    return this.closeLabel || hqCloseText(this.texts);
  }

  get effectiveWidth(): number {
    if (this.width && this.width > 0) return this.width;
    return HQ_DRAWER_WIDTHS[this.size ?? 'md'] ?? HQ_DRAWER_WIDTHS.md;
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open']) this.open ? this.show() : this.hide();
  }

  ngOnDestroy(): void {
    this.hide();
  }

  /** Asks the screen to close the drawer (it owns the open state). */
  requestClose(): void {
    this.closed.emit();
    this.openChange.emit(false);
  }

  private show(): void {
    if (this.ref) return;
    const rtl = (this.document.documentElement.getAttribute('dir') || this.document.body?.getAttribute('dir')) === 'rtl';
    // `end` is the right edge in LTR; mirrored in RTL. The global strategy works in physical left/right.
    const right = (this.position === 'end') !== rtl;
    const position = this.overlay.position().global().top('0');
    this.ref = this.overlay.create({
      hasBackdrop: true,
      backdropClass: HQ_DRAWER_BACKDROP_CLASS,
      panelClass: HQ_DRAWER_PANE_CLASS,
      height: '100%',
      maxWidth: '100vw',
      positionStrategy: right ? position.right('0') : position.left('0'),
      scrollStrategy: this.overlay.scrollStrategies.block(),
    });
    this.ref.attach(new TemplatePortal(this.panel, this.vcr));
    this.subs = new Subscription();
    this.subs.add(this.ref.backdropClick().subscribe(() => {
      if (this.closeOnBackdrop) this.requestClose();
    }));
    this.subs.add(this.ref.keydownEvents().subscribe((event) => {
      if (event.key === 'Escape' && this.closeOnEscape) {
        event.preventDefault();
        this.requestClose();
      }
    }));
  }

  private hide(): void {
    this.subs.unsubscribe();
    this.ref?.dispose();
    this.ref = null;
  }
}

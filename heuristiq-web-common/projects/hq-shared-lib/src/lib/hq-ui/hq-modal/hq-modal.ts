import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import {
  Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, TemplateRef, ViewChild,
  ViewContainerRef, booleanAttribute, inject,
} from '@angular/core';
import { Subscription } from 'rxjs';
import { HQ_UI_STATE_DEFAULT_TEXTS, HQ_UI_STATE_TEXTS, HqUiStateTexts, hqCloseText } from '../hq-view-states/hq-ui-state.texts';
import { HQ_MODAL_BACKDROP_CLASS, HQ_MODAL_DEFAULT_SIZE, HQ_MODAL_PANE_CLASS, HqModalSize } from './hq-modal.constants';

let nextModalId = 0;

/**
 * A modal dialog driven by the screen's own flag, for dialogs whose markup lives in the screen's template (the
 * replacement for Bootstrap `.modal` blocks). It opens above the page with a backdrop, keeps focus inside while open,
 * returns focus to where it was on close and locks page scroll. Close (X), the backdrop and Escape emit `closed` (and
 * `openChange(false)`); the screen then sets `open` to false. The body is the projected content; footer buttons go in an
 * element marked `hqModalFooter`.
 *
 *   <hq-modal [open]="showEdit" [title]="'rides.edit' | translate" size="lg" (closed)="showEdit = false">
 *     ...form...
 *     <div hqModalFooter>
 *       <button hqButton="secondary" (click)="showEdit = false">{{ 'common.cancel' | translate }}</button>
 *       <button hqButton (click)="save()">{{ 'common.save' | translate }}</button>
 *     </div>
 *   </hq-modal>
 */
@Component({
  selector: 'hq-modal',
  standalone: false,
  template: `
    <ng-template #dialog>
      <div class="hq-modal" [ngClass]="'hq-modal--' + size" role="dialog" aria-modal="true"
           [attr.aria-labelledby]="title ? titleId : null" [attr.aria-label]="!title ? (ariaLabel || null) : null"
           cdkTrapFocus [cdkTrapFocusAutoCapture]="true">
        @if (title || showClose) {
          <div class="hq-modal__header">
            <h2 class="hq-modal__title" [id]="titleId">{{ title }}</h2>
            @if (showClose) {
              <button type="button" class="hq-modal__close" [attr.aria-label]="closeText" [title]="closeText" (click)="requestClose()">
                <i class="bi bi-x-lg" aria-hidden="true"></i>
              </button>
            }
          </div>
        }
        <div class="hq-modal__body"><ng-content></ng-content></div>
        <div class="hq-modal__footer"><ng-content select="[hqModalFooter]"></ng-content></div>
      </div>
    </ng-template>
  `,
  styleUrl: './hq-modal.scss',
})
export class HqModal implements OnChanges, OnDestroy {
  private readonly overlay = inject(Overlay);
  private readonly vcr = inject(ViewContainerRef);
  private readonly texts: HqUiStateTexts = inject(HQ_UI_STATE_TEXTS, { optional: true }) ?? HQ_UI_STATE_DEFAULT_TEXTS;

  @Input({ transform: booleanAttribute }) open = false;
  /** Already-translated heading. */
  @Input() title = '';
  @Input() size: HqModalSize = HQ_MODAL_DEFAULT_SIZE;
  @Input({ transform: booleanAttribute }) closeOnBackdrop = true;
  @Input({ transform: booleanAttribute }) closeOnEscape = true;
  @Input({ transform: booleanAttribute }) showClose = true;
  /** Label of the X button; empty uses the app's close text. */
  @Input() closeLabel = '';
  /** Accessible name when there is no title. */
  @Input() ariaLabel = '';

  /** X, backdrop or Escape asked to close; the screen sets `open` to false. */
  @Output() closed = new EventEmitter<void>();
  @Output() openChange = new EventEmitter<boolean>();

  @ViewChild('dialog', { static: true }) private dialog!: TemplateRef<unknown>;

  readonly titleId = `hq-modal-title-${nextModalId++}`;
  private ref: OverlayRef | null = null;
  private subs = new Subscription();

  get closeText(): string {
    return this.closeLabel || hqCloseText(this.texts);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['open']) this.open ? this.show() : this.hide();
  }

  ngOnDestroy(): void {
    this.hide();
  }

  requestClose(): void {
    this.closed.emit();
    this.openChange.emit(false);
  }

  private show(): void {
    if (this.ref) return;
    this.ref = this.overlay.create({
      hasBackdrop: true,
      backdropClass: HQ_MODAL_BACKDROP_CLASS,
      panelClass: HQ_MODAL_PANE_CLASS,
      positionStrategy: this.overlay.position().global().centerHorizontally().centerVertically(),
      scrollStrategy: this.overlay.scrollStrategies.block(),
    });
    this.ref.attach(new TemplatePortal(this.dialog, this.vcr));
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

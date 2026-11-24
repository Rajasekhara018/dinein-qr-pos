import { AriaDescriber } from '@angular/cdk/a11y';
import { Directionality } from '@angular/cdk/bidi';
import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy, Component, ComponentRef, Directive, ElementRef, inject, Input, NgZone, OnChanges, OnDestroy,
  SimpleChanges, booleanAttribute, numberAttribute,
} from '@angular/core';
import {
  HQ_TOOLTIP_DEFAULT_POSITION, HQ_TOOLTIP_HIDE_DELAY_MS, HQ_TOOLTIP_LONG_PRESS_MS, HQ_TOOLTIP_POSITIONS,
  HQ_TOOLTIP_SHOW_DELAY_MS, HQ_TOOLTIP_TOUCH_HIDE_MS, HqTooltipPosition,
} from './hq-tooltip.constants';

/** The bubble itself, drawn in a CDK overlay pane by HqTooltip. Not used on its own. */
@Component({
  selector: 'hq-tooltip-panel',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'hq-tooltip', 'aria-hidden': 'true' },
  template: `{{ text }}`,
  styleUrl: './hq-tooltip.scss',
})
export class HqTooltipPanel {
  @Input() text = '';
}

/**
 * The application's tooltip: `<button [hqTooltip]="'orders.archive' | translate">`. Use it instead of `title` (which is slow,
 * cannot be styled, and shows "null" when bound to nothing).
 *
 * - Shows after a short hover, at once on keyboard focus, and on a long press on touch screens; Escape, a click, scrolling the
 *   element out of view or leaving it hides it.
 * - Empty text (null, undefined, '') shows nothing, so `[hqTooltip]="collapsed ? label : null"` is safe.
 * - `hqTooltipPosition`: top (default), bottom, start or end; it flips to the other side when there is no room, and start/end
 *   follow the reading direction.
 * - `hqTooltipIfTruncated`: only when the element's own text is cut off (an ellipsis cell showing its full value).
 * - Screen readers hear the text as the element's description (aria-describedby); the bubble itself is hidden from them. Give an
 *   icon-only button an `aria-label` as well: a tooltip describes, it does not name.
 * - A disabled button gets no pointer events: put the tooltip on a wrapping `<span>` to explain why it is disabled.
 *
 * The text must already be translated (the library has no translation dependency).
 */
@Directive({
  selector: '[hqTooltip]',
  standalone: false,
  exportAs: 'hqTooltip',
  host: {
    '(mouseenter)': 'onPointerEnter()',
    '(mouseleave)': 'scheduleHide()',
    '(focusin)': 'onFocus()',
    '(focusout)': 'scheduleHide()',
    '(mousedown)': 'hide()',
    '(keydown.escape)': 'onEscape($event)',
    '(touchstart)': 'onTouchStart()',
    '(touchend)': 'onTouchEnd()',
    '(touchcancel)': 'onTouchEnd()',
  },
})
export class HqTooltip implements OnChanges, OnDestroy {
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly overlay = inject(Overlay);
  private readonly zone = inject(NgZone);
  private readonly describer = inject(AriaDescriber);
  private readonly dir = inject(Directionality, { optional: true });

  /** The text to show; null, undefined or blank shows nothing. */
  @Input('hqTooltip') text: string | null | undefined = '';
  @Input('hqTooltipPosition') position: HqTooltipPosition = HQ_TOOLTIP_DEFAULT_POSITION;
  @Input({ alias: 'hqTooltipDisabled', transform: booleanAttribute }) disabled = false;
  /** Only show when the element's text is cut off (it is wider than its box). */
  @Input({ alias: 'hqTooltipIfTruncated', transform: booleanAttribute }) ifTruncated = false;
  @Input({ alias: 'hqTooltipShowDelay', transform: numberAttribute }) showDelay = HQ_TOOLTIP_SHOW_DELAY_MS;
  @Input({ alias: 'hqTooltipHideDelay', transform: numberAttribute }) hideDelay = HQ_TOOLTIP_HIDE_DELAY_MS;

  private overlayRef: OverlayRef | null = null;
  private panel: ComponentRef<HqTooltipPanel> | null = null;
  private showTimer: ReturnType<typeof setTimeout> | null = null;
  private hideTimer: ReturnType<typeof setTimeout> | null = null;
  private described = '';

  /** Whether the bubble is on screen. */
  get visible(): boolean {
    return !!this.overlayRef?.hasAttached();
  }

  private get message(): string {
    return (this.text ?? '').toString().trim();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['text']) {
      this.updateDescription();
      if (!this.message) {
        this.hide(0);
      } else if (this.panel) {
        this.panel.setInput('text', this.message);
        this.overlayRef?.updatePosition();
      }
    }
    if (changes['disabled'] && this.disabled) {
      this.hide(0);
    }
    if (changes['position'] && this.overlayRef) {
      // a new position needs a new strategy: the next show builds it
      this.dispose();
    }
  }

  ngOnDestroy(): void {
    this.clearTimers();
    this.dispose();
    if (this.described) {
      this.describer.removeDescription(this.host.nativeElement, this.described, 'tooltip');
    }
  }

  onPointerEnter(): void {
    this.show(this.showDelay);
  }

  /** Keyboard focus shows it at once; focus from a click does not (the click hides it). */
  onFocus(): void {
    if (this.focusVisible()) {
      this.show(0);
    }
  }

  onEscape(event: Event): void {
    if (this.visible) {
      event.stopPropagation();
      this.hide(0);
    }
  }

  onTouchStart(): void {
    this.show(HQ_TOOLTIP_LONG_PRESS_MS);
  }

  onTouchEnd(): void {
    if (this.visible) {
      this.hide(HQ_TOOLTIP_TOUCH_HIDE_MS);
    } else {
      this.clearTimers();
    }
  }

  scheduleHide(): void {
    this.hide(this.hideDelay);
  }

  /** Shows the tooltip after {@code delay} ms (when there is something to show). */
  show(delay = this.showDelay): void {
    if (this.disabled || !this.message || (this.ifTruncated && !this.truncated())) {
      return;
    }
    this.clearTimers();
    if (this.visible) {
      return;
    }
    if (delay <= 0) {
      this.attach();
      return;
    }
    this.zone.runOutsideAngular(() => {
      this.showTimer = setTimeout(() => this.zone.run(() => this.attach()), delay);
    });
  }

  /** Hides the tooltip after {@code delay} ms. */
  hide(delay = 0): void {
    this.clearTimers();
    if (!this.visible) {
      return;
    }
    if (delay <= 0) {
      this.overlayRef?.detach();
      this.panel = null;
      return;
    }
    this.zone.runOutsideAngular(() => {
      this.hideTimer = setTimeout(() => this.zone.run(() => this.hide(0)), delay);
    });
  }

  private attach(): void {
    if (!this.message || !this.host.nativeElement.isConnected) {
      return;
    }
    const ref = this.overlayRef ?? this.createOverlay();
    if (!ref.hasAttached()) {
      this.panel = ref.attach(new ComponentPortal(HqTooltipPanel));
      this.panel.setInput('text', this.message);
      this.panel.changeDetectorRef.detectChanges();
      ref.updatePosition();
    }
  }

  private createOverlay(): OverlayRef {
    const strategy = this.overlay.position()
      .flexibleConnectedTo(this.host)
      .withPositions(HQ_TOOLTIP_POSITIONS[this.position] ?? HQ_TOOLTIP_POSITIONS[HQ_TOOLTIP_DEFAULT_POSITION])
      .withFlexibleDimensions(false)
      .withViewportMargin(8)
      .withPush(true);
    this.overlayRef = this.overlay.create({
      positionStrategy: strategy,
      scrollStrategy: this.overlay.scrollStrategies.reposition({ scrollThrottle: 20 }),
      panelClass: 'hq-tooltip-pane',
      direction: this.dir?.value ?? 'ltr',
    });
    // the element scrolled out of view: hide rather than float over something else
    strategy.positionChanges.subscribe((change) => {
      if (change.scrollableViewProperties.isOriginClipped && this.visible) {
        this.zone.run(() => this.hide(0));
      }
    });
    this.overlayRef.detachments().subscribe(() => (this.panel = null));
    return this.overlayRef;
  }

  private dispose(): void {
    this.overlayRef?.dispose();
    this.overlayRef = null;
    this.panel = null;
  }

  private clearTimers(): void {
    if (this.showTimer) { clearTimeout(this.showTimer); this.showTimer = null; }
    if (this.hideTimer) { clearTimeout(this.hideTimer); this.hideTimer = null; }
  }

  /** The tooltip text as the element's accessible description, unless it only repeats the element's own name. */
  private updateDescription(): void {
    const element = this.host.nativeElement;
    if (this.described) {
      this.describer.removeDescription(element, this.described, 'tooltip');
      this.described = '';
    }
    const message = this.message;
    const name = (element.getAttribute('aria-label') ?? element.textContent ?? '').trim();
    if (message && message !== name) {
      this.describer.describe(element, message, 'tooltip');
      this.described = message;
    }
  }

  private truncated(): boolean {
    const element = this.host.nativeElement;
    return element.scrollWidth > element.clientWidth || element.scrollHeight > element.clientHeight;
  }

  private focusVisible(): boolean {
    try {
      return this.host.nativeElement.matches(':focus-visible') || !!this.host.nativeElement.querySelector(':focus-visible');
    } catch {
      return true;
    }
  }
}

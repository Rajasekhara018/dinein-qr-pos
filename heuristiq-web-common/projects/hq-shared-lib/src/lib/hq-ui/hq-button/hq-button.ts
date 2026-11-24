import { booleanAttribute, Directive, ElementRef, inject, Input, OnInit } from '@angular/core';
import {
  HQ_BUTTON_APPEARANCES,
  HQ_BUTTON_COLORS,
  HQ_BUTTON_DEFAULT_APPEARANCE,
  HQ_BUTTON_DEFAULT_COLOR,
  HQ_BUTTON_DEFAULT_SIZE,
  HQ_BUTTON_SIZES,
  HqButtonAppearance,
  HqButtonColor,
  HqButtonSize,
} from './hq-button.constants';

/**
 * The common button: a native `<button>` or `<a>` styled by the active theme (light, dark and every named theme),
 * so primary, accent, warn and success look right everywhere without per-screen colours. It is a directive, so
 * the element keeps its own `type`, `(click)`, `routerLink`, `title`, `*hqHasPrivilege` and content.
 *
 * ```html
 * <button hqButton (click)="save()">Save</button>                                  <!-- primary, filled -->
 * <button hqButton="accent" type="submit" [loading]="saving">Create</button>
 * <button hqButton="warn" appearance="outline" size="sm" (click)="remove()">Delete</button>
 * <button hqButton="secondary" (click)="cancel()">Cancel</button>
 * <button hqButton="primary" appearance="text" iconOnly [attr.aria-label]="'Edit'"><i class="bi bi-pencil"></i></button>
 * <a hqButton="success" block routerLink="/pay">Pay now</a>
 * ```
 *
 * - `hqButton`: `primary` (also a bare attribute) | `accent` | `warn` | `success` | `secondary`.
 * - `appearance`: `filled` (default) | `outline` | `text`. `size`: `sm` | `md` (default) | `lg`.
 * - `loading` shows a spinner, sets `aria-busy` and disables the button (a link gets `aria-disabled`); when it ends the
 *   button's own `[disabled]` applies again. `disabled` is an input of this directive, so `[disabled]="x"` keeps working.
 * - `iconOnly` makes a square button for a single icon (give it an aria-label); `block` makes it full width.
 * - A `<button>` without a `type` gets `type="button"`, so it never submits a form by accident; `type="submit"` is kept.
 */
@Directive({
  selector: 'button[hqButton], a[hqButton]',
  standalone: false,
  host: {
    class: 'hq-btn',
    '[class]': 'variantClasses',
    '[class.hq-btn--loading]': 'loading',
    '[class.hq-btn--icon]': 'iconOnly',
    '[class.hq-btn--block]': 'block',
    '[class.hq-btn--disabled]': 'isInactive',
    '[attr.disabled]': 'isButton && isInactive ? "" : null',
    '[attr.aria-disabled]': '!isButton && isInactive ? "true" : null',
    '[attr.tabindex]': '!isButton && isInactive ? -1 : null',
    '[attr.aria-busy]': 'loading ? "true" : null',
  },
})
export class HqButton implements OnInit {
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);

  private color: HqButtonColor = HQ_BUTTON_DEFAULT_COLOR;

  /** The colour role; a bare `hqButton` attribute (empty value) is primary. */
  @Input()
  set hqButton(value: HqButtonColor | '' | null | undefined) {
    this.color = (HQ_BUTTON_COLORS as readonly string[]).includes(value ?? '') ? (value as HqButtonColor) : HQ_BUTTON_DEFAULT_COLOR;
  }
  get hqButton(): HqButtonColor {
    return this.color;
  }

  @Input() appearance: HqButtonAppearance = HQ_BUTTON_DEFAULT_APPEARANCE;
  @Input() size: HqButtonSize = HQ_BUTTON_DEFAULT_SIZE;
  @Input({ transform: booleanAttribute }) loading = false;
  @Input({ transform: booleanAttribute }) disabled = false;
  @Input({ transform: booleanAttribute }) iconOnly = false;
  @Input({ transform: booleanAttribute }) block = false;

  get isButton(): boolean {
    return this.host.nativeElement.tagName === 'BUTTON';
  }

  /** Disabled by the author or busy loading. */
  get isInactive(): boolean {
    return this.disabled || this.loading;
  }

  get variantClasses(): string {
    const appearance = (HQ_BUTTON_APPEARANCES as readonly string[]).includes(this.appearance) ? this.appearance : HQ_BUTTON_DEFAULT_APPEARANCE;
    const size = (HQ_BUTTON_SIZES as readonly string[]).includes(this.size) ? this.size : HQ_BUTTON_DEFAULT_SIZE;
    return `hq-btn--${this.color} hq-btn--${appearance} hq-btn--${size}`;
  }

  ngOnInit(): void {
    const el = this.host.nativeElement;
    if (this.isButton && !el.hasAttribute('type')) el.setAttribute('type', 'button');
  }
}

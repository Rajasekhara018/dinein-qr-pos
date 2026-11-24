import { Component, ContentChild, Directive, Input, booleanAttribute } from '@angular/core';
import { HQ_CARD_DEFAULT_PADDING, HQ_CARD_DEFAULT_VARIANT, HqCardPadding, HqCardVariant } from './hq-card.constants';

/** Marks content for the right side of a hq-card header: `<div hqCardActions>...buttons...</div>`. */
@Directive({ selector: '[hqCardActions]', standalone: false })
export class HqCardActions {}

/** Marks content for the footer of a hq-card: `<div hqCardFooter>...buttons...</div>`. */
@Directive({ selector: '[hqCardFooter]', standalone: false })
export class HqCardFooter {}

/**
 * The shared panel ("card") of every screen, drawn from the theme tokens so it follows all themes.
 * The body is the projected content; header actions go in `[hqCardActions]`, the footer in `[hqCardFooter]`.
 * The header is drawn only when there is a title, subtitle, icon or header action; the footer only when projected.
 *
 *   <hq-card [title]="'wallet.balance' | translate" icon="bi-wallet2">
 *     <div hqCardActions><button hqButton size="sm" (click)="refresh()">...</button></div>
 *     ...body...
 *     <div hqCardFooter><button hqButton (click)="save()">...</button></div>
 *   </hq-card>
 *
 * `clickable` only adds the hover / focus look (pointer, lift); the screen adds the click handler (and, when the
 * card is the only control, role / tabindex) itself. `padding="none"` suits a card that wraps a table.
 */
@Component({
  selector: 'hq-card',
  standalone: false,
  host: {
    class: 'hq-card',
    '[class.hq-card--default]': "variant === 'default'",
    '[class.hq-card--outlined]': "variant === 'outlined'",
    '[class.hq-card--flat]': "variant === 'flat'",
    '[class.hq-card--highlight]': "variant === 'highlight'",
    '[class.hq-card--pad-none]': "padding === 'none'",
    '[class.hq-card--pad-sm]': "padding === 'sm'",
    '[class.hq-card--pad-md]': "padding === 'md'",
    '[class.hq-card--pad-lg]': "padding === 'lg'",
    '[class.hq-card--clickable]': 'clickable',
  },
  template: `
    @if (hasHeader) {
      <div class="hq-card__header">
        @if (icon) {<span class="hq-card__icon" aria-hidden="true"><i class="bi" [ngClass]="icon"></i></span>}
        @if (title || subtitle) {
          <div class="hq-card__heading">
            @if (title) {<h3 class="hq-card__title">{{ title }}</h3>}
            @if (subtitle) {<p class="hq-card__subtitle">{{ subtitle }}</p>}
          </div>
        }
        <div class="hq-card__actions"><ng-content select="[hqCardActions]"></ng-content></div>
      </div>
    }
    <div class="hq-card__body"><ng-content></ng-content></div>
    @if (footer) {
      <div class="hq-card__footer"><ng-content select="[hqCardFooter]"></ng-content></div>
    }
  `,
  styleUrl: './hq-card.scss',
})
export class HqCard {
  /** Already-translated heading. */
  @Input() title = '';
  /** Already-translated line under the heading. */
  @Input() subtitle = '';
  /** bootstrap-icons class shown before the heading, e.g. 'bi-wallet2'. */
  @Input() icon = '';
  @Input() padding: HqCardPadding = HQ_CARD_DEFAULT_PADDING;
  @Input() variant: HqCardVariant = HQ_CARD_DEFAULT_VARIANT;
  /** Hover / focus look of a card the screen makes clickable. */
  @Input({ transform: booleanAttribute }) clickable = false;

  @ContentChild(HqCardActions) actions?: HqCardActions;
  @ContentChild(HqCardFooter) footer?: HqCardFooter;

  get hasHeader(): boolean {
    return !!(this.title || this.subtitle || this.icon || this.actions);
  }
}

import { booleanAttribute, computed, Directive, ElementRef, inject, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

const BASE =
  'inline-flex min-w-0 select-none items-center justify-center gap-2 rounded-control font-semibold transition-colors transition-shadow ' +
  'disabled:cursor-not-allowed disabled:opacity-50 aria-disabled:cursor-not-allowed aria-disabled:opacity-50 ' +
  'focus-visible:outline-3 focus-visible:outline-offset-2 focus-visible:outline-focus';

const VARIANTS: Record<ButtonVariant, string> = {
  primary: 'bg-brand text-brand-contrast shadow-sm hover:bg-brand-strong hover:shadow-md active:shadow-sm',
  secondary: 'bg-surface-muted text-ink hover:bg-surface-sunken',
  outline: 'border border-line-strong bg-surface text-ink hover:bg-surface-muted',
  ghost: 'bg-transparent text-ink hover:bg-surface-muted',
  danger: 'bg-danger text-white hover:bg-red-800',
};

// Every size keeps a ≥ 44px touch target.
const SIZES: Record<ButtonSize, string> = {
  sm: 'min-h-touch px-3 text-sm',
  md: 'min-h-touch px-4 text-base',
  lg: 'min-h-12 px-5 text-base sm:text-lg',
};

/**
 * Styled button/link: `<button appButton variant="primary" size="lg" block>Pay</button>`.
 * Sets `aria-busy` while `loading`, and disables native buttons.
 */
@Directive({
  selector: 'button[appButton], a[appButton]',
  standalone: false,
  host: {
    '[class]': 'classes()',
    '[attr.aria-busy]': 'loading() || null',
    '[attr.disabled]': 'isButton && (disabled() || loading()) ? "" : null',
    '[attr.aria-disabled]': '!isButton && disabled() ? "true" : null',
  },
})
export class ButtonDirective {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly block = input(false, { transform: booleanAttribute });
  readonly loading = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });

  protected readonly isButton =
    (inject<ElementRef<HTMLElement>>(ElementRef).nativeElement.tagName ?? '').toUpperCase() ===
    'BUTTON';

  protected readonly classes = computed(
    () =>
      `${BASE} ${VARIANTS[this.variant()]} ${SIZES[this.size()]} ${this.block() ? 'w-full' : ''}`,
  );
}

/**
 * Round icon-only button with a 44px touch target. Always give it an `aria-label`.
 * `<button appIconButton aria-label="Close"><svg …/></button>`
 */
@Directive({
  selector: 'button[appIconButton], a[appIconButton]',
  standalone: false,
  host: {
    '[class]': 'classes()',
  },
})
export class IconButtonDirective {
  readonly variant = input<'ghost' | 'solid' | 'outline'>('ghost');

  protected readonly classes = computed(() => {
    const base =
      'inline-flex size-touch shrink-0 items-center justify-center rounded-full transition-colors ' +
      'disabled:cursor-not-allowed disabled:opacity-50';
    switch (this.variant()) {
      case 'solid':
        return `${base} bg-brand text-brand-contrast hover:bg-brand-strong`;
      case 'outline':
        return `${base} border border-line-strong bg-surface text-ink hover:bg-surface-muted`;
      default:
        return `${base} text-ink hover:bg-surface-muted`;
    }
  });
}

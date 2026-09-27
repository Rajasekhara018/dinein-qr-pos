import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type AuthShellTheme = 'admin' | 'dark' | 'light';

export interface AuthShellBenefit {
  readonly title: string;
  readonly description: string;
}

/**
 * Two-column split auth screen (admin login, phase 4): a story/benefits panel on desktop (≥1024px, `lg:`) beside a
 * form panel that always shows. Below that breakpoint the story panel disappears and a compact centred header
 * (projected icon + title) takes its place — no horizontal scroll at any width. Purely presentational: callers
 * project their own `<form>` (and anything else, e.g. a role notice) as default content.
 *
 * Themes (see `frontend/src/styles.css` for the tokens):
 *  - `admin`: the fixed green/gold `--admin-brand`/`--admin-accent` palette and Poppins headings (phase 4).
 *  - `dark`: a near-black neutral panel with the restaurant's own `--brand` colour on the icon chips — kitchen,
 *    which is dark by default.
 *  - `light`: a light-neutral panel, same `--brand` accent chips — waiter, which has no brand identity of its own
 *    but is staff-facing like kitchen/admin, so the restaurant's brand colour is contextually correct there too.
 */
@Component({
  selector: 'app-auth-shell',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': 'hostClasses()' },
  templateUrl: './auth-shell.html',
})
export class AuthShell {
  readonly theme = input<AuthShellTheme>('admin');
  /** Small pill label at the top of the desktop story panel, e.g. "Restaurant back-office". Omit to hide it. */
  readonly eyebrow = input('');
  readonly headline = input('');
  readonly description = input('');
  /** Numbered benefit rows under the headline. Empty = the story panel centres the headline/description instead. */
  readonly benefits = input<AuthShellBenefit[]>([]);
  /** Shown with the projected `[authShellIcon]` icon, centred, below `lg`. */
  readonly mobileTitle = input.required<string>();
  readonly mobileSubtitle = input('');
  /** Shown left-aligned, without the icon, at `lg` and above. Falls back to `mobileTitle`/`mobileSubtitle`. */
  readonly desktopTitle = input('');
  readonly desktopSubtitle = input('');

  protected readonly hostClasses = computed(() =>
    this.theme() === 'admin' ? 'admin-scope font-admin-sans' : '',
  );

  protected readonly headingFontClass = computed(() =>
    this.theme() === 'admin' ? 'font-admin-heading' : 'font-display',
  );

  protected readonly panelClasses = computed(() => {
    const base = this.benefits().length ? 'justify-between' : 'justify-center';
    switch (this.theme()) {
      case 'admin':
        return `${base} bg-admin-brand text-admin-brand-contrast`;
      case 'dark':
        return `${base} bg-neutral-950 text-neutral-50`;
      case 'light':
        return `${base} bg-neutral-900 text-neutral-50`;
    }
  });

  protected readonly badgeClasses = computed(() =>
    this.theme() === 'admin' ? 'bg-white/15 text-admin-brand-contrast' : 'bg-white/10 text-neutral-50',
  );

  protected readonly mutedClasses = computed(() =>
    this.theme() === 'admin' ? 'text-admin-brand-contrast/85' : 'text-neutral-400',
  );

  /** Numbered marker style for each benefit row: plain accent text for admin, a solid brand chip otherwise. */
  protected readonly numberClasses = computed(() =>
    this.theme() === 'admin'
      ? 'text-lg font-bold text-admin-accent'
      : 'flex size-8 shrink-0 items-center justify-center rounded-full bg-brand text-sm font-bold text-brand-contrast',
  );

  protected readonly mobileIconWrapClasses = computed(() =>
    this.theme() === 'admin' ? 'bg-admin-brand text-admin-brand-contrast' : 'bg-brand text-brand-contrast',
  );

  protected pad(n: number): string {
    return n < 10 ? `0${n}` : `${n}`;
  }
}

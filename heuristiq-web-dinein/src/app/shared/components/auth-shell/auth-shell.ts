import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type AuthShellTheme = 'admin' | 'staff';

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
 *  - `staff`: kitchen and waiter — no fixed brand identity, so the panel uses the ordinary semantic surface tokens
 *    (which is why it needs no dark/light variant of its own: `bg-surface-muted` already flips automatically under
 *    a `.dark` ancestor, e.g. kitchen's dark-by-default theme) with the restaurant's own `--brand` colour on the
 *    icon chips and numbered markers — contextually correct for staff screens the same way kitchen's board already
 *    uses `--brand` for its own accents.
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
    return this.theme() === 'admin'
      ? `${base} bg-admin-brand text-admin-brand-contrast`
      : `${base} border-r border-line bg-surface-muted text-ink`;
  });

  protected readonly badgeClasses = computed(() =>
    this.theme() === 'admin'
      ? 'bg-white/15 text-admin-brand-contrast'
      : 'border border-line-strong bg-surface text-ink-muted',
  );

  protected readonly mutedClasses = computed(() =>
    this.theme() === 'admin' ? 'text-admin-brand-contrast/85' : 'text-ink-muted',
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

import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

/**
 * An application's logo (a one-colour SVG glyph under assets/apps) painted in the current text colour: the SVG is used as a CSS
 * mask over `currentColor`, so the logo follows the selected theme, its accent palette and dark mode with no per-theme files.
 * Decorative: the application's name is always shown next to it, so it is hidden from assistive technology.
 */
@Component({
  selector: 'app-area-logo',
  standalone: true,
  template: '',
  styleUrl: './app-area-logo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[style.--app-area-logo]': 'maskImage',
  },
})
export class AppAreaLogo {
  /** Path of the SVG glyph, relative to the app's base address (e.g. assets/apps/bestride.svg). */
  @Input({ required: true }) src = '';

  /** The `--app-area-logo` value the stylesheet masks with. */
  get maskImage(): string {
    return `url("${this.src}")`;
  }
}

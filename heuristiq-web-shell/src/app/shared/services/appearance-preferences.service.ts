import { Injectable, inject } from '@angular/core';
import { HqAppearanceService } from 'hq-shared-lib';
import { UserPreference } from '../models/uam.models';
import { ThemeService } from './theme-service';
import { AppearanceChoice, choiceFromPreference } from './appearance-preferences.helpers';

/** The theme pair the header's light/dark toggle uses; a saved old LIGHT / DARK choice maps onto it. */
const LIGHT_THEME = 'light';
const DARK_THEME = 'dark';

/**
 * Puts the look a user chose under Profile > Preferences onto the page: the theme, the text size and the comfort options.
 * Used after sign-in (with the saved preference) and while the preferences form is open (to preview a choice before saving).
 */
@Injectable({ providedIn: 'root' })
export class AppearancePreferencesService {
  private readonly theme = inject(ThemeService);
  private readonly appearance = inject(HqAppearanceService);

  /** Applies a saved preference; nothing happens when the user has none. */
  applySaved(preference: UserPreference | null | undefined): void {
    if (!preference) {
      return;
    }
    this.apply(choiceFromPreference(preference, LIGHT_THEME, DARK_THEME));
  }

  /** Applies a choice at once. An empty theme leaves the browser's current theme alone. */
  apply(choice: AppearanceChoice): void {
    if (choice.themeId && choice.themeId !== this.theme.currentTheme) {
      this.theme.setTheme(choice.themeId);
    }
    this.appearance.apply({
      fontScale: choice.fontScale, compact: choice.compact, animations: choice.animations,
      highContrast: choice.highContrast, reducedMotion: choice.reducedMotion, screenReader: choice.screenReader,
    });
  }
}

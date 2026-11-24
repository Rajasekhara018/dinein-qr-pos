import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqScreenHelpContent, HqScreenHelpTexts } from 'hq-shared-lib';

/** Prefix of the translation keys of every screen's help (`screenHelp.<screen>.what|step1..6|tip`). */
export const SCREEN_HELP_I18N = 'screenHelp';

/** Most steps a screen's help can list. */
export const SCREEN_HELP_MAX_STEPS = 6;

/** The words hq-shared-lib's help panel shows, read from this app's translation files in the user's language. */
export function screenHelpTexts(): HqScreenHelpTexts {
  const t = inject(TranslateService);
  const text = (screen: string, key: string): string => {
    const full = `${SCREEN_HELP_I18N}.${screen}.${key}`;
    const value = t.instant(full) as string;
    return value && value !== full ? value : '';
  };
  return {
    title: () => t.instant(`${SCREEN_HELP_I18N}.title`),
    read: (screen: string): HqScreenHelpContent | null => {
      const what = text(screen, 'what');
      if (!what) return null;
      const steps = Array.from({ length: SCREEN_HELP_MAX_STEPS }, (_, i) => text(screen, 'step' + (i + 1))).filter((s) => !!s);
      return { what, steps, tip: text(screen, 'tip') };
    },
  };
}

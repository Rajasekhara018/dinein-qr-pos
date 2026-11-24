import { HQ_DARK_THEME_IDS, HQ_FONT_SCALES, HQ_THEME_IDS, HqFontScale, HqThemeId } from 'hq-shared-lib';
import { UserPreference } from '../models/uam.models';

/** Everything a user sets under Profile > Preferences that changes how the page looks. */
export interface AppearanceChoice {
  /** The chosen theme; '' keeps whatever theme the browser already has. */
  themeId: HqThemeId | '';
  fontScale: HqFontScale;
  compact: boolean;
  animations: boolean;
  highContrast: boolean;
  reducedMotion: boolean;
  screenReader: boolean;
}

export const APPEARANCE_CHOICE_DEFAULTS: Readonly<AppearanceChoice> = {
  themeId: '', fontScale: 'normal', compact: false, animations: true, highContrast: false, reducedMotion: false, screenReader: false,
};

/** A theme id the apps know, or '' (an unknown id from an older or newer server is ignored, never applied). */
export function knownThemeId(value: string | null | undefined): HqThemeId | '' {
  return value && (HQ_THEME_IDS as readonly string[]).includes(value) ? (value as HqThemeId) : '';
}

/** A text size the apps offer, else normal. */
export function knownFontScale(value: string | null | undefined): HqFontScale {
  return value && (HQ_FONT_SCALES as readonly string[]).includes(value) ? (value as HqFontScale) : 'normal';
}

/**
 * The look a saved preference asks for. A preference saved before themes could be picked only has the old LIGHT / DARK
 * choice; it asks for the app's own light or dark theme only when nothing else was chosen (so an old DARK is still dark).
 */
export function choiceFromPreference(preference: UserPreference | null | undefined, lightTheme: HqThemeId, darkTheme: HqThemeId): AppearanceChoice {
  const ui = preference?.ui;
  const accessibility = preference?.accessibility;
  let themeId = knownThemeId(ui?.themeId);
  if (!themeId && ui?.theme === 'DARK') {
    themeId = darkTheme;
  } else if (!themeId && ui?.theme === 'LIGHT') {
    themeId = lightTheme;
  }
  return {
    themeId,
    fontScale: knownFontScale(ui?.fontScale),
    compact: !!ui?.compactMode,
    animations: ui?.animationsEnabled ?? true,
    highContrast: !!accessibility?.highContrast,
    reducedMotion: !!accessibility?.reducedMotion,
    screenReader: !!accessibility?.screenReader,
  };
}

/** The old LIGHT / DARK value to store next to the theme id, so apps and clients that only know it still follow the choice. */
export function legacyThemeFor(themeId: string): 'LIGHT' | 'DARK' | null {
  if (!themeId) {
    return null;
  }
  return (HQ_DARK_THEME_IDS as readonly string[]).includes(themeId) ? 'DARK' : 'LIGHT';
}

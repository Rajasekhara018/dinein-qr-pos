import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, switchMap } from 'rxjs';
import { UserPreference } from '../models/uam.models';
import { AppearancePreferencesService } from './appearance-preferences.service';
import { AuthService } from './auth-service';
import { LanguageService } from './language-service';
import { languageCodeFromEnum, languageEnumFromCode, preferredLanguageCode } from './language-preference.helpers';
import { UamService } from './uam-service';

/**
 * Keeps the person's own look and language with their account: after sign-in the saved theme and language are put on the page
 * (whatever this browser remembered from someone else), and a theme or language chosen in the header is saved back so it is
 * there at the next sign-in on any device. Saving is best effort: a failure never blocks or alters what the person sees.
 */
@Injectable({ providedIn: 'root' })
export class SessionPreferencesService {
  private readonly uam = inject(UamService);
  private readonly appearance = inject(AppearancePreferencesService);
  private readonly language = inject(LanguageService);
  private readonly auth = inject(AuthService);

  /**
   * Applies the signed-in person's saved theme, text size, comfort options and language. The language is the one chosen under
   * Preferences > Regional, else the preferred language of the profile; with neither, the browser's own choice stays.
   */
  applyAfterSignIn(): Observable<void> {
    return this.uam.getMyPreferences().pipe(
      switchMap((preference) => {
        this.appearance.applySaved(preference);
        const fromPreference = preferredLanguageCode(preference);
        if (fromPreference) {
          this.language.useSaved(fromPreference);
          return of(undefined);
        }
        return this.uam.getMyProfile().pipe(map((profile) => this.language.useSaved(languageCodeFromEnum(profile.preferredLanguage))));
      }),
      map(() => undefined),
    );
  }

  /** Saves the language chosen in the header on the account (the rest of the saved preferences is kept). */
  saveLanguage(code: string): void {
    const language = languageEnumFromCode(code);
    if (language) {
      this.save((saved) => ({ regional: { ...(saved?.regional ?? {}), language } }));
    }
  }

  /** Saves the theme chosen in the header on the account (the rest of the saved preferences is kept). */
  saveTheme(themeId: string): void {
    this.save((saved) => ({ ui: { ...(saved?.ui ?? {}), themeId } }));
  }

  private save(change: (saved: UserPreference | null) => Partial<UserPreference>): void {
    if (!this.auth.currentUser) {
      return;
    }
    this.uam.getMyPreferences().pipe(
      // Without the saved preferences nothing is written: a partial save could blank the other sections.
      switchMap((saved) => this.uam.updateMyPreferences({
        applicationId: saved?.applicationId ?? null, ui: saved?.ui ?? null, notification: saved?.notification ?? null,
        dashboard: saved?.dashboard ?? null, accessibility: saved?.accessibility ?? null, regional: saved?.regional ?? null,
        ...change(saved),
      })),
      catchError(() => of(null)),
    ).subscribe();
  }
}

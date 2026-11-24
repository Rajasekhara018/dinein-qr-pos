import { Component, DOCUMENT, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { catchError, distinctUntilChanged, filter, map, of, switchMap } from 'rxjs';
import { AuthService } from './shared/services/auth-service';
import { BrandingService } from './shared/services/branding-service';
import { SessionPreferencesService } from './shared/services/session-preferences.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.html',
  standalone: false,
  styleUrl: './app.scss',
})
export class App {
  constructor() {
    // index.html is static and served before Angular bootstraps, so its <title>/favicon can't read
    // the runtime branding JSON -- apply it here instead. App is the bootstrap component, so it is
    // only created after AppModule's provideAppInitializer (BrandingService.load) has resolved.
    const { branding } = inject(BrandingService);
    const doc = inject(DOCUMENT);
    inject(Title).setTitle(branding.appName);
    const favicon = doc.querySelector<HTMLLinkElement>("link[rel~='icon']");
    if (favicon) favicon.href = branding.favicon;

    // The look a user saved (theme, text size, comfort options) is applied each time someone signs in; before that, and when the
    // saved preference cannot be read, the page keeps the look remembered in this browser.
    const session = inject(SessionPreferencesService);
    inject(AuthService).user$.pipe(
      map((user) => user?.id ?? null),
      distinctUntilChanged(),
      filter((id): id is string => !!id),
      // A failed read must not end the stream: the next sign-in still applies the saved look and language.
      switchMap(() => session.applyAfterSignIn().pipe(catchError(() => of(undefined)))),
      takeUntilDestroyed(),
    ).subscribe();
  }
}

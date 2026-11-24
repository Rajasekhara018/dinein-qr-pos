import { HQ_DEFAULT_THEMES, provideHqStaleBundleRecovery } from 'hq-shared-lib';
import {
  NgModule,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';

import { AppRoutingModule } from './app-routing-module';
import { App } from './app';
import { SharedModule } from './shared/shared-module';
import { Layout } from './layout/layout';
import { Header } from './layout/header/header';
import { Sidebar } from './layout/sidebar/sidebar';
import { authInterceptorFn } from './shared/interceptors/auth-interceptor';
import { NotificationsModule } from './notifications/notifications-module';
import { ThemeService, THEME_STORAGE_KEY } from './shared/services/theme-service';
import { BrandingService } from './shared/services/branding-service';
import { ScreenViewService } from './shared/services/screen-view-service';
import { IdleTimeoutService } from './shared/services/idle-timeout-service';
import { SessionOverlayService } from './shared/services/session-overlay.service';
import { AuthService } from './shared/services/auth-service';
import { StaleBundleRecovery } from './shared/services/stale-bundle-recovery';
import { NgIdleModule } from '@ng-idle/core';
import { TranslateModule } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { DEFAULT_LANGUAGE, LanguageService } from './shared/services/language-service';

@NgModule({
  declarations: [
    App,
    Layout,
    Header,
    Sidebar,
  ],
  imports: [
    BrowserModule,
    BrowserAnimationsModule,
    FormsModule,
    ReactiveFormsModule,
    AppRoutingModule,
    NotificationsModule,
    SharedModule,
    // Idle-session timeout (see IdleTimeoutService): provides the @ng-idle watcher it drives.
    NgIdleModule.forRoot(),
    // i18n: root TranslateService + HTTP loader for src/assets/i18n/{lang}.json.
    // ngx-translate v17's TranslateHttpLoader takes no constructor args (the old
    // `useFactory: (http) => new TranslateHttpLoader(http, prefix)` form is gone) -- it's
    // configured via provideTranslateHttpLoader. useHttpBackend bypasses HTTP interceptors so
    // the auth interceptor never touches static translation file requests.
    TranslateModule.forRoot({
      loader: provideTranslateHttpLoader({
        prefix: './assets/i18n/',
        suffix: '.json',
        useHttpBackend: true,
      }),
      fallbackLang: DEFAULT_LANGUAGE,
    }),
  ],
  providers: [
    // A tab left open across a deployment asks for chunks the new build no longer has: reload once to get the new build.
    provideHqStaleBundleRecovery(),
    provideBrowserGlobalErrorListeners(),
    // Zone-based change detection: the view refreshes after every async event (HTTP responses,
    // socket messages, timers, SDK callbacks). AdminWeb's pages run inside this app as a
    // federated remote and are written for zone.js, so the host has to provide it.
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideHttpClient(withInterceptors([authInterceptorFn])),
    { provide: THEME_STORAGE_KEY, useValue: 'railcab_web_theme' },
    // First visit (no saved choice): the Pasupu Kumkuma theme, its dark version when the OS prefers dark; the header toggle flips between the two.
    { provide: HQ_DEFAULT_THEMES, useValue: { light: 'pasupu', dark: 'pasupu-dark' } },
    // Fetch the per-deployment branding JSON (environment.configFile) before bootstrap completes,
    // so App and every component can read BrandingService.branding synchronously. load() never
    // rejects -- it falls back to default branding on failure.
    provideAppInitializer(() => inject(BrandingService).load()),
    // Activate the persisted UI language (localStorage 'app_language', default 'en') and load its
    // JSON before bootstrap completes, so nothing renders in English first. init() never rejects.
    provideAppInitializer(() => inject(LanguageService).init()),
    // ThemeService (providedIn: 'root') only applies <html data-theme="..."> from its own
    // constructor, and otherwise only gets constructed whenever something first injects it --
    // today that's only Header, which doesn't exist on the pre-auth/full-bleed routes (login,
    // register, forgot-password, change-password). Without this, those pages always rendered the
    // default light theme regardless of what the user actually has selected. Just injecting it
    // here (no async work, unlike the two initializers above) forces that constructor to run
    // before the first route renders, so every page honors the saved theme from the first paint.
    provideAppInitializer(() => { inject(ThemeService); }),
    // Starts reporting opened screens to the audit trail (router NavigationEnd -> batched,
    // best-effort POST; never throws or blocks navigation).
    provideAppInitializer(() => { inject(ScreenViewService).start(); }),
    // Warns, then signs out, a signed-in user who has been inactive (see IdleTimeoutService).
    provideAppInitializer(() => { inject(IdleTimeoutService).start(); }),
    // Closes the dialogs and snackbar an ended session left open once the app reaches the sign-in page (see SessionOverlayService).
    provideAppInitializer(() => { inject(SessionOverlayService).start(); }),
    // Reloads once when a screen's code file is gone because a new version was deployed under an open tab.
    provideAppInitializer(() => { inject(StaleBundleRecovery).start(); }),
    // Refreshes which screens the signed-in user may open (UAM privileges) after a page load.
    provideAppInitializer(() => { inject(AuthService).refreshPrivileges(); }),
  ],
  bootstrap: [App],
})
export class AppModule {}

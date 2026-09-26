import {
  ErrorHandler,
  isDevMode,
  LOCALE_ID,
  NgModule,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { DATE_PIPE_DEFAULT_OPTIONS, registerLocaleData } from '@angular/common';
import localeEnIn from '@angular/common/locales/en-IN';
import {
  provideHttpClient,
  withFetch,
  withInterceptors,
  withXsrfConfiguration,
} from '@angular/common/http';
import { BrowserModule } from '@angular/platform-browser';
import { ServiceWorkerModule } from '@angular/service-worker';

import { AppRoutingModule } from './app-routing-module';
import { App } from './app';
import { CoreModule } from './core/core-module';
import { authInterceptor } from './core/http/auth.interceptor';
import { csrfInterceptor, XSRF_COOKIE, XSRF_HEADER } from './core/http/csrf.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { AppErrorHandler } from './core/ui/app-error-handler';
import { IST_OFFSET } from './core/util/time';
import { NotFound } from './pages/not-found/not-found';

registerLocaleData(localeEnIn, 'en-IN');

@NgModule({
  declarations: [App, NotFound],
  imports: [
    BrowserModule,
    AppRoutingModule,
    CoreModule,
    ServiceWorkerModule.register('ngsw-worker.js', {
      enabled: !isDevMode(),
      // Register once the app is stable or after 30 seconds, whichever comes first.
      registrationStrategy: 'registerWhenStable:30000',
    }),
  ],
  providers: [
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(
      withFetch(),
      // Order matters: errorInterceptor is outermost so it sees the final outcome after auth/CSRF retries.
      withInterceptors([errorInterceptor, csrfInterceptor, authInterceptor]),
      withXsrfConfiguration({ cookieName: XSRF_COOKIE, headerName: XSRF_HEADER }),
    ),
    { provide: LOCALE_ID, useValue: 'en-IN' },
    // Every DatePipe renders in IST (Asia/Kolkata has no DST, so a fixed offset is exact).
    {
      provide: DATE_PIPE_DEFAULT_OPTIONS,
      useValue: { timezone: IST_OFFSET, dateFormat: 'd MMM y, h:mm a' },
    },
    { provide: ErrorHandler, useClass: AppErrorHandler },
  ],
  bootstrap: [App],
})
export class AppModule {}

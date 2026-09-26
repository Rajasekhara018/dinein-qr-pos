import {
  HttpClient,
  HttpEventType,
  provideHttpClient,
  withInterceptors,
  withXsrfConfiguration,
} from '@angular/common/http';
import { createEnvironmentInjector, EnvironmentInjector, inject, Injectable } from '@angular/core';
import { filter, map, Observable } from 'rxjs';
import { UploadResult } from '../../../../core/api/models';
import { authInterceptor } from '../../../../core/http/auth.interceptor';
import { csrfInterceptor, XSRF_COOKIE, XSRF_HEADER } from '../../../../core/http/csrf.interceptor';
import { errorInterceptor } from '../../../../core/http/error.interceptor';
import { silentErrors } from '../../../../core/http/http-context';

export type UploadEvent =
  | { kind: 'progress'; percent: number }
  | { kind: 'done'; result: UploadResult };

/**
 * `POST /api/admin/images` with real upload progress.
 *
 * The app's HttpClient uses `withFetch()`, and the Fetch API cannot report upload progress. This service owns a
 * child HttpClient without `withFetch()` (so Angular uses its XHR backend) but with the same interceptor chain
 * (auth token + silent refresh, CSRF, ApiError normalisation), so behaviour is identical apart from progress.
 */
@Injectable({ providedIn: 'root' })
export class ImageUploadService {
  private readonly http = createEnvironmentInjector(
    [
      provideHttpClient(
        withInterceptors([errorInterceptor, csrfInterceptor, authInterceptor]),
        withXsrfConfiguration({ cookieName: XSRF_COOKIE, headerName: XSRF_HEADER }),
      ),
    ],
    inject(EnvironmentInjector),
  ).get(HttpClient);

  upload(file: Blob, fileName: string): Observable<UploadEvent> {
    const form = new FormData();
    form.append('file', file, fileName);
    return this.http
      .post<UploadResult>('/api/admin/images', form, {
        reportProgress: true,
        observe: 'events',
        context: silentErrors(),
      })
      .pipe(
        filter(
          (event) =>
            event.type === HttpEventType.UploadProgress || event.type === HttpEventType.Response,
        ),
        map((event): UploadEvent => {
          if (event.type === HttpEventType.Response) {
            return { kind: 'done', result: event.body as UploadResult };
          }
          const total = event.total ?? file.size;
          const percent = total ? Math.min(99, Math.round((event.loaded / total) * 100)) : 0;
          return { kind: 'progress', percent };
        }),
      );
  }
}

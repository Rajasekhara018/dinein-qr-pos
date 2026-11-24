import { Injectable, inject } from '@angular/core';
import { WebSearchApiService } from '../search/web-search-api.service';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { TranslateService } from '@ngx-translate/core';

interface ApiResponse<T> {
  status?: boolean;
  success?: boolean;
  data?: T;
  responseObject?: T;
  message?: string;
  errorMsg?: string;
  errorCode?: string;
  timestamp?: string;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly search = inject(WebSearchApiService);

  getAll<T>(model: string, filters: Record<string, unknown> = {}): Observable<T> {
    return this.search.sourceGetAll<T>(model, filters);
  }
  private base = environment.apiUrl;

  constructor(private http: HttpClient, private translate: TranslateService) {}

  private buildParams(params?: Record<string, any>): HttpParams {
    let p = new HttpParams();
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== null && v !== '') {
          p = p.set(k, String(v));
        }
      });
    }
    return p;
  }

  private unwrap<T>(response: T | ApiResponse<T>): T {
    if (response && typeof response === 'object' && ('status' in response || 'success' in response || 'responseObject' in response)) {
      const apiResponse = response as ApiResponse<T>;
      const success = apiResponse.success ?? apiResponse.status ?? true;
      if (!success) {
        const fallback = this.translate.instant('shared.errors.requestFailed');
        throw {
          error: {
            detail: apiResponse.message || apiResponse.errorMsg || fallback,
            message: apiResponse.message || apiResponse.errorMsg || fallback,
            errorCode: apiResponse.errorCode,
          },
        };
      }
      return (apiResponse.responseObject ?? apiResponse.data) as T;
    }
    return response as T;
  }

  get<T>(path: string, params?: Record<string, any>): Observable<T> {
    return this.http.get<T | ApiResponse<T>>(`${this.base}${path}`, { params: this.buildParams(params) })
      .pipe(map((response) => this.unwrap<T>(response)));
  }

  post<T>(path: string, body?: any, options?: any): Observable<T> {
    return (this.http.post<T | ApiResponse<T>>(`${this.base}${path}`, body, options) as Observable<T | ApiResponse<T>>)
      .pipe(map((response) => this.unwrap<T>(response)));
  }

  patch<T>(path: string, body?: any): Observable<T> {
    return this.http.patch<T | ApiResponse<T>>(`${this.base}${path}`, body)
      .pipe(map((response) => this.unwrap<T>(response)));
  }

  put<T>(path: string, body?: any): Observable<T> {
    return this.http.put<T | ApiResponse<T>>(`${this.base}${path}`, body)
      .pipe(map((response) => this.unwrap<T>(response)));
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T | ApiResponse<T>>(`${this.base}${path}`)
      .pipe(map((response) => this.unwrap<T>(response)));
  }

  getBlob(path: string, params?: Record<string, any>): Observable<Blob> {
    return this.http.get(`${this.base}${path}`, {
      params: this.buildParams(params),
      responseType: 'blob',
    });
  }

  postMultipart<T>(path: string, formData: FormData, options?: any): Observable<T> {
    return (this.http.post<T | ApiResponse<T>>(`${this.base}${path}`, formData, options) as Observable<T | ApiResponse<T>>)
      .pipe(map((response) => this.unwrap<T>(response)));
  }
}

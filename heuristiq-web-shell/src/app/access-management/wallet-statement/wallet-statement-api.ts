import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Observable, catchError, from, map, switchMap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UamCommonResponse } from '../../shared/models/uam.models';
import { statementQuery, toStatementFile } from './wallet-statement.helpers';
import {
  StatementFile, StatementFormat, StatementMonths, StatementQuery, WalletStatement, YearSummary,
} from './wallet-statement.models';

/**
 * The statement endpoints of Rider_WalletService (gateway /api/v1/wallet/**). The caller and the organization always come
 * from the sign-in token; the admin routes only reach accounts of the caller's own organization.
 */
@Injectable({ providedIn: 'root' })
export class WalletStatementApi {
  private readonly base = environment.walletApiUrl;

  constructor(private http: HttpClient, private translate: TranslateService) {}

  /** One page of a month's statement. With {@code userId} the administrator reads a person's account by user. */
  statement(query: StatementQuery): Observable<WalletStatement> {
    return this.get<WalletStatement>(`${this.root(query, true)}${statementQuery(query)}`);
  }

  /** The months that have activity, newest first. */
  months(query: StatementQuery): Observable<StatementMonths> {
    return this.get<StatementMonths>(`${this.root(query)}/months${statementQuery({ ...query, month: undefined, page: undefined, size: undefined })}`);
  }

  /** Opening, credits, debits and closing for each month of a year. */
  summary(query: StatementQuery, year: number): Observable<YearSummary> {
    const plain = { ...query, month: undefined, page: undefined, size: undefined };
    return this.get<YearSummary>(`${this.root(query)}/summary${statementQuery(plain, { year })}`);
  }

  /** Downloads a month as a file (an authenticated blob request, so the sign-in token goes along). */
  export(query: StatementQuery, format: StatementFormat): Observable<StatementFile> {
    const plain = { ...query, page: undefined, size: undefined };
    return this.http
      .get(`${this.root(query)}/export${statementQuery(plain, { format })}`, { responseType: 'blob', observe: 'response' })
      .pipe(
        map((response: HttpResponse<Blob>) => toStatementFile(response, query.month ?? '', format)),
        catchError((error: HttpErrorResponse) => this.readableError(error)),
      );
  }

  private root(query: StatementQuery, allowUser = false): string {
    if (query.scope === 'self') return `${this.base}/me/statement`;
    if (allowUser && query.userId) return `${this.base}/admin/users/${encodeURIComponent(query.userId)}/statement`;
    return `${this.base}/admin/accounts/${encodeURIComponent(query.accountId ?? '')}/statement`;
  }

  /** A failed blob request carries its JSON error as a Blob; parse it so the status and message can be read. */
  private readableError(error: HttpErrorResponse): Observable<never> {
    if (!(error.error instanceof Blob)) return throwError(() => error);
    return from(error.error.text()).pipe(
      map((text) => this.parse(text)),
      switchMap((body) => throwError(() => ({ status: error.status, error: body }))),
    );
  }

  private parse(text: string): unknown {
    try {
      return JSON.parse(text);
    } catch {
      return {};
    }
  }

  private get<T>(url: string): Observable<T> {
    return this.http.get<UamCommonResponse<T>>(url).pipe(map((res) => this.unwrap(res)));
  }

  private unwrap<T>(res: UamCommonResponse<T>): T {
    if (!res.success) {
      const fallback = this.translate.instant('shared.errors.requestFailed');
      throw { error: { detail: res.message || fallback, message: res.message || fallback } };
    }
    return res.responseObject as T;
  }
}

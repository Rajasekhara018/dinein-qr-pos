import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { WalletStatementApi } from './wallet-statement-api';

const base = environment.walletApiUrl;
const ok = (responseObject: unknown) => ({ success: true, message: '', responseObject });

describe('WalletStatementApi', () => {
  let api: WalletStatementApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(WalletStatementApi);
    http = TestBed.inject(HttpTestingController);
  });

  it('reads the own statement with account, month, page and size', () => {
    let result: unknown;
    api.statement({ scope: 'self', account: 'WALLET', month: '2026-03', page: 1, size: 50 }).subscribe((r) => (result = r));
    const req = http.expectOne(`${base}/me/statement?account=WALLET&month=2026-03&page=1&size=50`);
    expect(req.request.method).toBe('GET');
    req.flush(ok({ openingBalance: { code: 'INR', value: 1 } }));
    expect(result).toEqual({ openingBalance: { code: 'INR', value: 1 } });
  });

  it('reads months, the year summary and the admin routes', () => {
    api.months({ scope: 'self', account: 'WALLET', month: '2026-03' }).subscribe();
    http.expectOne(`${base}/me/statement/months?account=WALLET`).flush(ok({ months: [] }));
    api.summary({ scope: 'self', account: 'WALLET', app: 'RIDE', month: '2026-03' }, 2026).subscribe();
    http.expectOne(`${base}/me/statement/summary?account=WALLET&app=RIDE&year=2026`).flush(ok({}));
    api.statement({ scope: 'admin', accountId: 'a/1', month: '2026-03', page: 0, size: 25 }).subscribe();
    http.expectOne(`${base}/admin/accounts/a%2F1/statement?month=2026-03&page=0&size=25`).flush(ok({}));
    api.statement({ scope: 'admin', userId: 'u1', account: 'WALLET', page: 0, size: 25 }).subscribe();
    http.expectOne(`${base}/admin/users/u1/statement?account=WALLET&page=0&size=25`).flush(ok({}));
    api.months({ scope: 'admin', accountId: 'a1' }).subscribe();
    http.expectOne(`${base}/admin/accounts/a1/statement/months`).flush(ok({ months: [] }));
  });

  it('turns an unsuccessful envelope into an error', () => {
    let error: { error: { message: string } } | undefined;
    api.statement({ scope: 'self' }).subscribe({ error: (e) => (error = e) });
    http.expectOne(`${base}/me/statement`).flush({ success: false, message: 'Nope', responseObject: null });
    expect(error?.error.message).toBe('Nope');
  });

  it('downloads a file as a blob with the name from the header', () => {
    let file: { filename: string; blob: Blob } | undefined;
    api.export({ scope: 'self', account: 'WALLET', month: '2026-03', page: 2 }, 'csv').subscribe((f) => (file = f));
    const req = http.expectOne(`${base}/me/statement/export?account=WALLET&month=2026-03&format=csv`);
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['a,b']), { headers: { 'Content-Disposition': 'attachment; filename="s.csv"' } });
    expect(file?.filename).toBe('s.csv');
  });

  it('falls back to statement-YYYY-MM.ext when the header is missing', () => {
    let name = '';
    api.export({ scope: 'admin', accountId: 'a1', month: '2026-03' }, 'pdf').subscribe((f) => (name = f.filename));
    http.expectOne(`${base}/admin/accounts/a1/statement/export?month=2026-03&format=pdf`).flush(new Blob(['x']));
    expect(name).toBe('statement-2026-03.pdf');
  });

  it('reads the JSON error body of a failed download', async () => {
    const failure = new Promise<{ status: number; error: { errorCode: string } }>((resolve) => {
      api.export({ scope: 'self', month: '2026-03' }, 'csv').subscribe({ error: resolve });
    });
    http.expectOne((r) => r.url.startsWith(`${base}/me/statement/export`)).flush(
      new Blob([JSON.stringify({ errorCode: 'STATEMENT_TOO_LARGE' })]), { status: 413, statusText: 'Too Large' });
    const error = await failure;
    expect(error.status).toBe(413);
    expect(error.error.errorCode).toBe('STATEMENT_TOO_LARGE');
  });

  it('survives a failed download whose body is not JSON', async () => {
    const failure = new Promise<{ status: number; error: unknown }>((resolve) => {
      api.export({ scope: 'self', month: '2026-03' }, 'pdf').subscribe({ error: resolve });
    });
    http.expectOne((r) => r.url.startsWith(`${base}/me/statement/export`)).flush(new Blob(['<html>']), { status: 502, statusText: 'Bad' });
    const error = await failure;
    expect(error.status).toBe(502);
    expect(error.error).toEqual({});
  });
});

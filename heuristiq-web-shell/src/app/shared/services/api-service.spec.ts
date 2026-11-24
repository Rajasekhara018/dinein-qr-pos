import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { ApiService } from './api-service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;
  const url = (p: string) => `${environment.apiUrl}${p}`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useValue: { instant: (k: string) => k } }],
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GET drops empty params and stringifies the rest', () => {
    api.get('/x', { a: 1, b: '', c: null, d: undefined, e: false }).subscribe();
    const req = http.expectOne((r) => r.url === url('/x'));
    expect(req.request.params.keys().sort()).toEqual(['a', 'e']);
    expect(req.request.params.get('a')).toBe('1');
    expect(req.request.params.get('e')).toBe('false');
    req.flush({});
  });

  it('GET works without params', () => {
    api.get('/x').subscribe();
    http.expectOne((r) => r.url === url('/x')).flush({});
  });

  it('unwraps responseObject, then data, from a successful envelope', () => {
    const out: unknown[] = [];
    api.get('/a').subscribe((v) => out.push(v));
    http.expectOne((r) => r.url === url('/a')).flush({ success: true, responseObject: { id: 1 } });
    api.get('/b').subscribe((v) => out.push(v));
    http.expectOne((r) => r.url === url('/b')).flush({ status: true, data: [2] });
    expect(out).toEqual([{ id: 1 }, [2]]);
  });

  it('returns a plain body untouched', () => {
    let v: unknown;
    api.get('/c').subscribe((r) => (v = r));
    http.expectOne((r) => r.url === url('/c')).flush({ id: 5 });
    expect(v).toEqual({ id: 5 });
  });

  it('raises the server message from a failed envelope, with the error code', () => {
    let err: unknown;
    api.get('/d').subscribe({ error: (e) => (err = e) });
    http.expectOne((r) => r.url === url('/d')).flush({ success: false, message: 'denied', errorCode: 'E1' });
    expect(err).toEqual({ error: { detail: 'denied', message: 'denied', errorCode: 'E1' } });
  });

  it('falls back to errorMsg, then to the translated generic message', () => {
    const errs: unknown[] = [];
    api.get('/e').subscribe({ error: (e) => errs.push(e) });
    http.expectOne((r) => r.url === url('/e')).flush({ status: false, errorMsg: 'bad' });
    api.get('/f').subscribe({ error: (e) => errs.push(e) });
    http.expectOne((r) => r.url === url('/f')).flush({ status: false });
    expect(errs).toEqual([
      { error: { detail: 'bad', message: 'bad', errorCode: undefined } },
      { error: { detail: 'shared.errors.requestFailed', message: 'shared.errors.requestFailed', errorCode: undefined } },
    ]);
  });

  it('POST, PATCH, PUT, DELETE use the right method and body and unwrap', () => {
    const out: unknown[] = [];
    api.post('/p', { a: 1 }).subscribe((v) => out.push(v));
    const post = http.expectOne(url('/p'));
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ a: 1 });
    post.flush({ success: true, responseObject: 'P' });

    api.patch('/p', { b: 2 }).subscribe((v) => out.push(v));
    const patch = http.expectOne(url('/p'));
    expect(patch.request.method).toBe('PATCH');
    patch.flush({ success: true, responseObject: 'PA' });

    api.put('/p', { c: 3 }).subscribe((v) => out.push(v));
    const put = http.expectOne(url('/p'));
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ c: 3 });
    put.flush({ success: true, responseObject: 'PU' });

    api.delete('/p').subscribe((v) => out.push(v));
    const del = http.expectOne(url('/p'));
    expect(del.request.method).toBe('DELETE');
    del.flush({ success: true, responseObject: 'D' });
    expect(out).toEqual(['P', 'PA', 'PU', 'D']);
  });

  it('fetches a blob with params', () => {
    let blob: Blob | undefined;
    api.getBlob('/file', { id: 7 }).subscribe((b) => (blob = b));
    const req = http.expectOne((r) => r.url === url('/file'));
    expect(req.request.responseType).toBe('blob');
    expect(req.request.params.get('id')).toBe('7');
    req.flush(new Blob(['x']));
    expect(blob?.size).toBe(1);
  });

  it('posts multipart form data and unwraps the reply', () => {
    const form = new FormData();
    form.append('f', 'v');
    let v: unknown;
    api.postMultipart('/up', form).subscribe((r) => (v = r));
    const req = http.expectOne(url('/up'));
    expect(req.request.body).toBe(form);
    req.flush({ success: true, responseObject: 'ok' });
    expect(v).toBe('ok');
  });
});

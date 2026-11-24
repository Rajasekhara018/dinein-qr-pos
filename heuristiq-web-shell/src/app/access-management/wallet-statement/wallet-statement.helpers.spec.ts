import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { describe, expect, it, vi } from 'vitest';
import {
  defaultMonth, filenameFrom, isDebit, isTooLarge, labelKey, pageCount, saveBlob, statementQuery, toStatementFile, yearOf,
} from './wallet-statement.helpers';
import { StatementLine } from './wallet-statement.models';

describe('wallet statement helpers', () => {
  it('labelKey returns the key or null', () => {
    expect(labelKey({ A: 'k.a' }, 'A')).toBe('k.a');
    expect(labelKey({ A: 'k.a' }, 'B')).toBeNull();
    expect(labelKey({ A: 'k.a' }, null)).toBeNull();
  });

  it('statementQuery sends account only for self or user route, app only off the user route, and skips blanks', () => {
    expect(statementQuery({ scope: 'self', account: 'WALLET', app: ' ', month: '2026-03', page: 0, size: 50 }))
      .toBe('?account=WALLET&month=2026-03&page=0&size=50');
    expect(statementQuery({ scope: 'admin', accountId: 'a1', account: 'WALLET', app: 'RIDE', month: '2026-03' }))
      .toBe('?app=RIDE&month=2026-03');
    expect(statementQuery({ scope: 'admin', userId: 'u1', account: 'HELD', app: 'RIDE' })).toBe('?account=HELD');
    expect(statementQuery({ scope: 'admin', accountId: 'a1' }, { year: 2026 })).toBe('?year=2026');
    expect(statementQuery({ scope: 'admin', accountId: 'a1' })).toBe('');
  });

  it('defaultMonth is the newest month with activity, else the current month', () => {
    expect(defaultMonth([{ month: '2026-02', count: 3 }, { month: '2026-01', count: 1 }])).toBe('2026-02');
    expect(defaultMonth([], new Date(2026, 8, 5))).toBe('2026-09');
    expect(defaultMonth(null, new Date(2026, 0, 5))).toBe('2026-01');
    expect(defaultMonth([{ month: 'bad', count: 1 }], new Date(2026, 10, 5))).toBe('2026-11');
  });

  it('yearOf reads the year', () => {
    expect(yearOf('2025-12')).toBe(2025);
  });

  it('isDebit tells debits from credits', () => {
    expect(isDebit({ debit: { code: 'INR', value: 5 }, credit: null } as StatementLine)).toBe(true);
    expect(isDebit({ debit: null, credit: { code: 'INR', value: 5 } } as StatementLine)).toBe(false);
    expect(isDebit({ debit: { code: 'INR', value: null }, credit: { code: 'INR', value: 5 } } as StatementLine)).toBe(false);
    // An older answer sends the amount as text.
    expect(isDebit({ debit: '5.00', credit: null } as unknown as StatementLine)).toBe(true);
    expect(isDebit({ debit: '', credit: '5.00' } as unknown as StatementLine)).toBe(false);
  });

  it('filenameFrom uses Content-Disposition or falls back', () => {
    expect(filenameFrom('attachment; filename="stmt-2026-03.csv"', '2026-03', 'csv')).toBe('stmt-2026-03.csv');
    expect(filenameFrom('attachment; filename=plain.pdf', '2026-03', 'pdf')).toBe('plain.pdf');
    expect(filenameFrom("attachment; filename*=UTF-8''a%20b.csv", '2026-03', 'csv')).toBe('a b.csv');
    expect(filenameFrom('attachment; filename="../x/y.csv"', '2026-03', 'csv')).toBe('.._x_y.csv');
    expect(filenameFrom("attachment; filename*=UTF-8''%E0%A4%A", '2026-03', 'csv')).toContain('%E0');
    expect(filenameFrom(null, '2026-03', 'pdf')).toBe('statement-2026-03.pdf');
    expect(filenameFrom('attachment', '2026-03', 'csv')).toBe('statement-2026-03.csv');
  });

  it('toStatementFile wraps the body with the header name', () => {
    const blob = new Blob(['x']);
    const named = new HttpResponse<Blob>({ body: blob, headers: new HttpHeaders({ 'Content-Disposition': 'attachment; filename="a.csv"' }) });
    expect(toStatementFile(named, '2026-03', 'csv')).toEqual({ blob, filename: 'a.csv' });
    const empty = toStatementFile(new HttpResponse<Blob>({ body: null }), '2026-03', 'pdf');
    expect(empty.filename).toBe('statement-2026-03.pdf');
    expect(empty.blob.type).toBe('application/pdf');
  });

  it('isTooLarge recognises the 413 status and the error code', () => {
    expect(isTooLarge({ status: 413 })).toBe(true);
    expect(isTooLarge({ status: 400, error: { errorCode: 'STATEMENT_TOO_LARGE' } })).toBe(true);
    expect(isTooLarge({ status: 500, error: {} })).toBe(false);
    expect(isTooLarge(null)).toBe(false);
  });

  it('saveBlob clicks a hidden link and revokes the object url', () => {
    const click = vi.fn();
    const anchor = { click, remove: vi.fn(), style: {} as Record<string, string>, href: '', download: '', rel: '' };
    const doc = { createElement: vi.fn().mockReturnValue(anchor), body: { appendChild: vi.fn() } } as unknown as Document;
    const urls = { createObjectURL: vi.fn().mockReturnValue('blob:1'), revokeObjectURL: vi.fn() };
    saveBlob({ blob: new Blob(['x']), filename: 'f.csv' }, doc, urls);
    expect(anchor.href).toBe('blob:1');
    expect(anchor.download).toBe('f.csv');
    expect(click).toHaveBeenCalled();
    expect(urls.revokeObjectURL).toHaveBeenCalledWith('blob:1');
  });

  it('pageCount is at least one', () => {
    expect(pageCount(0)).toBe(1);
    expect(pageCount(null)).toBe(1);
    expect(pageCount(4)).toBe(4);
  });
});

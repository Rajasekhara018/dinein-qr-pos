import { hqAmountValue } from 'hq-shared-lib';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import {
  EXPORT_MIME, MONTH_PATTERN, STATEMENT_TOO_LARGE_CODE, STATEMENT_TOO_LARGE_STATUS,
} from './wallet-statement.constants';
import { StatementFile, StatementFormat, StatementLine, StatementMonth, StatementQuery } from './wallet-statement.models';

/** The translation key for a code, or null when the map has none (the caller then shows the raw code). */
export function labelKey(map: Readonly<Record<string, string>>, code: string | null | undefined): string | null {
  return code ? map[code] ?? null : null;
}

/** The query string (with leading ?) of the filters; empty values are left out. */
export function statementQuery(query: StatementQuery, extra: Record<string, string | number | undefined> = {}): string {
  const params = new URLSearchParams();
  const values: Record<string, string | number | undefined> = {
    account: query.scope === 'self' || query.userId ? query.account : undefined,
    app: query.scope === 'self' || (query.accountId && !query.userId) ? query.app : undefined,
    month: query.month,
    page: query.page,
    size: query.size,
    ...extra,
  };
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      params.set(key, String(value).trim());
    }
  }
  const text = params.toString();
  return text ? `?${text}` : '';
}

/** The month (YYYY-MM) a statement opens on: the newest one with activity, else the current month of the browser. */
export function defaultMonth(months: readonly StatementMonth[] | null | undefined, now: Date = new Date()): string {
  const latest = months?.find((m) => MONTH_PATTERN.test(m.month));
  if (latest) return latest.month;
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/** The year part of a YYYY-MM month. */
export function yearOf(month: string): number {
  return Number(month.slice(0, 4));
}

/** True when a line took money out (a debit) rather than in. */
export function isDebit(line: StatementLine): boolean {
  return hqAmountValue(line.debit) !== null;
}

/** The file name from a Content-Disposition header, else statement-YYYY-MM.ext. */
export function filenameFrom(disposition: string | null | undefined, month: string, format: StatementFormat): string {
  const fallback = `statement-${month}.${format}`;
  if (!disposition) return fallback;
  const star = /filename\*\s*=\s*(?:UTF-8'')?([^;]+)/i.exec(disposition);
  const plain = /filename\s*=\s*("([^"]*)"|[^;]+)/i.exec(disposition);
  let raw = star ? star[1] : plain ? (plain[2] ?? plain[1]) : '';
  try {
    raw = star ? decodeURIComponent(raw.trim()) : raw.trim();
  } catch {
    raw = raw.trim();
  }
  const name = raw.replace(/[\\/:*?"<>|\u0000-\u001f]/g, '_').trim();
  return name || fallback;
}

/** Wraps a download response as a file. */
export function toStatementFile(response: HttpResponse<Blob>, month: string, format: StatementFormat): StatementFile {
  const blob = response.body ?? new Blob([], { type: EXPORT_MIME[format] });
  return { blob, filename: filenameFrom(response.headers.get('Content-Disposition'), month, format) };
}

/** True when an export failed because the month is over the server's line cap. */
export function isTooLarge(error: unknown): boolean {
  const failure = error as Partial<HttpErrorResponse> | null;
  if (!failure) return false;
  if (failure.status === STATEMENT_TOO_LARGE_STATUS) return true;
  const body = failure.error as { errorCode?: unknown } | null | undefined;
  return !!body && typeof body === 'object' && body.errorCode === STATEMENT_TOO_LARGE_CODE;
}

/** Hands a blob to the browser as a download under a name. */
export function saveBlob(
  file: StatementFile,
  doc: Document = document,
  urls: Pick<typeof URL, 'createObjectURL' | 'revokeObjectURL'> = URL,
): void {
  const url = urls.createObjectURL(file.blob);
  const anchor = doc.createElement('a');
  anchor.href = url;
  anchor.download = file.filename;
  anchor.rel = 'noopener';
  anchor.style.display = 'none';
  doc.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  urls.revokeObjectURL(url);
}

/** Number of pages to offer, never below one. */
export function pageCount(totalPages: number | null | undefined): number {
  return Math.max(totalPages ?? 0, 1);
}

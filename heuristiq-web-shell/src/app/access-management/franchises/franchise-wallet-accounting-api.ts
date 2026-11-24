import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { HqAmount } from 'hq-shared-lib';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { UamCommonResponse } from '../../shared/models/uam.models';
import { OWNER_ACCOUNTING_DAY_END, OWNER_ACCOUNTING_DAY_START, OWNER_SHARE_KEYS } from './franchise-earnings.constants';
import {
  OwnerAccountingFilter, OwnerAccountingPayout, OwnerAccountingShare, OwnerAccountingTotals, OwnerAccountingTransaction,
  OwnerFranchiseAccounting,
} from './franchise-wallet-accounting.models';

type Raw = Readonly<Record<string, unknown>>;

/**
 * The franchise owner's own accounting, read live from Rider_WalletService (`/me/franchises/{id}/accounting`, privilege
 * WALLET_FRANCHISE_ACCOUNTING_MINE_READ; the wallet answers 403 unless the franchise is in the token's franchiseIds). The body
 * is `{franchiseId, summary{rows, totals}, shares, payable{available, pending, paidOut}, payouts, recentTransactions}`; all
 * wire mapping lives in this file.
 */
@Injectable({ providedIn: 'root' })
export class FranchiseWalletAccountingApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.walletApiUrl;

  mine(franchiseId: string, filter: OwnerAccountingFilter): Observable<OwnerFranchiseAccounting> {
    let params = new HttpParams();
    if (filter.app) params = params.set('app', filter.app);
    if (filter.from) params = params.set('from', `${filter.from}${OWNER_ACCOUNTING_DAY_START}`);
    if (filter.to) params = params.set('to', `${filter.to}${OWNER_ACCOUNTING_DAY_END}`);
    return this.http
      .get<UamCommonResponse<unknown>>(`${this.base}/me/franchises/${encodeURIComponent(franchiseId)}/accounting`, { params })
      .pipe(
        map((res) => {
          if (!res?.success) throw { error: { message: res?.message ?? '', detail: res?.message ?? '' } };
          return toOwnerAccounting(res.responseObject, franchiseId);
        }),
      );
  }
}

function isRecord(value: unknown): value is Raw {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function record(value: unknown): Raw {
  return isRecord(value) ? value : {};
}

/** Rows of a list answer: an array or a page (`content`). */
export function rowsOf(raw: unknown): Raw[] {
  const list = Array.isArray(raw) ? raw : isRecord(raw) ? raw['content'] : null;
  return Array.isArray(list) ? list.filter(isRecord) : [];
}

function text(row: Raw, key: string): string | null {
  const value = row[key];
  if (typeof value === 'string' && value !== '') return value;
  if (typeof value === 'number') return String(value);
  return null;
}

/** `{ code, value }`, or null when the wallet recorded none. */
export function toAmount(value: unknown): HqAmount | null {
  if (!isRecord(value) || typeof value['code'] !== 'string') return null;
  const amount = Number(value['value']);
  if (value['value'] === null || value['value'] === undefined || !Number.isFinite(amount)) return null;
  return { code: value['code'], value: amount };
}

function money(row: Raw, key: string): HqAmount | null {
  return toAmount(row[key]);
}

function toTotals(raw: unknown): OwnerAccountingTotals {
  const row = record(raw);
  const shares = record(row['shares']);
  const count = Number(row['count']);
  return {
    count: row['count'] !== null && row['count'] !== undefined && Number.isFinite(count) ? count : 0,
    gross: money(row, 'gross'),
    refunds: money(row, 'refunds'),
    owner: money(shares, 'owner'),
    agent: money(shares, 'agent'),
  };
}

/** The shares object as rows, in the wallet's order, leaving out roles with nothing recorded. */
function toShares(raw: unknown): OwnerAccountingShare[] {
  const shares = record(raw);
  return OWNER_SHARE_KEYS.map(([key, shareParty]) => ({ shareParty, amount: money(shares, key) })).filter((s) => s.amount !== null);
}

function toPayout(row: Raw): OwnerAccountingPayout {
  return {
    payoutId: text(row, 'payoutId') ?? '',
    amount: money(row, 'amount'),
    status: text(row, 'status') ?? '',
    createdAt: text(row, 'createdAt'),
    paidAt: text(row, 'paidAt'),
  };
}

function toTransaction(row: Raw): OwnerAccountingTransaction {
  return {
    journalId: text(row, 'journalId') ?? '',
    postedAt: text(row, 'postedAt'),
    appCode: text(row, 'appCode'),
    eventType: text(row, 'eventType'),
    journalType: text(row, 'journalType') ?? '',
    channel: text(row, 'channel'),
    amount: money(row, 'amount'),
    status: text(row, 'status'),
  };
}

/** The wallet's answer mapped to the screen model. */
export function toOwnerAccounting(raw: unknown, franchiseId: string): OwnerFranchiseAccounting {
  const row = record(raw);
  const payable = record(row['payable']);
  return {
    franchiseId: text(row, 'franchiseId') ?? franchiseId,
    totals: toTotals(record(row['summary'])['totals']),
    shares: toShares(row['shares']),
    payable: { available: money(payable, 'available'), pending: money(payable, 'pending'), paidOut: money(payable, 'paidOut') },
    payouts: rowsOf(row['payouts']).map(toPayout),
    recentTransactions: rowsOf(row['recentTransactions']).map(toTransaction),
  };
}

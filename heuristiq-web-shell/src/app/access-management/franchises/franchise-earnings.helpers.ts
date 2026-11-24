import { hqAmount, hqAmountCode } from 'hq-shared-lib';
import { CommissionParty, EarningsAccount, EarningsFilter, LedgerFilter, PayoutRequestBody, StatementSummary } from './franchise.models';

/** What the payout form holds. */
export interface PayoutForm {
  franchiseId: string;
  party: string;
  amount: number | null;
  currency: string;
  /** Optional: the application paying it (needed only when the franchise has several). */
  appCode?: string;
  note?: string;
}

/** The translation key for a code, or null when the map has none (the caller then shows the raw code). */
export function labelKey(map: Readonly<Record<string, string>>, code: string | null | undefined): string | null {
  return code ? map[code] ?? null : null;
}

/** The query string (with leading ?) of the filter and page; empty values are left out. */
export function ledgerQuery(filter: LedgerFilter, page?: number, size?: number): string {
  const params = new URLSearchParams();
  if (filter.franchiseId) { params.set('franchiseId', filter.franchiseId); }
  if (filter.party) { params.set('party', filter.party); }
  if (filter.from) { params.set('from', filter.from); }
  if (filter.to) { params.set('to', filter.to); }
  if (page !== undefined) { params.set('page', String(page)); }
  if (size !== undefined) { params.set('size', String(size)); }
  const text = params.toString();
  return text ? `?${text}` : '';
}

/** The query string (with leading ?) of an earnings account, view, period and page; empty values are left out. */
export function earningsQuery(filter: EarningsFilter, page?: number, size?: number): string {
  const params = new URLSearchParams();
  params.set('account', filter.account);
  if (filter.account === 'FRANCHISE' && filter.franchiseId) { params.set('franchiseId', filter.franchiseId); }
  if (filter.state) { params.set('state', filter.state); }
  if (filter.from) { params.set('from', filter.from); }
  if (filter.to) { params.set('to', filter.to); }
  if (page !== undefined) { params.set('page', String(page)); }
  if (size !== undefined) { params.set('size', String(size)); }
  return `?${params.toString()}`;
}

/** A stable key of an earnings account for a picker: "AGENT", or "FRANCHISE:<franchiseId>". */
export function accountKey(account: Pick<EarningsAccount, 'ownerType' | 'ownerId'>): string {
  return account.ownerType === 'FRANCHISE' ? `FRANCHISE:${account.ownerId}` : 'AGENT';
}

/** The account a picker key names (the caller's own agent account for anything that is not a franchise key). */
export function accountFromKey(key: string): Pick<EarningsFilter, 'account' | 'franchiseId'> {
  return key.startsWith('FRANCHISE:') ? { account: 'FRANCHISE', franchiseId: key.slice('FRANCHISE:'.length) } : { account: 'AGENT' };
}

/** Translation key of what is wrong with a payout form, or null when it can be sent. */
/**
 * The parties a payout can be asked as: the owner (the form lists only franchises the caller owns) plus any other payable party
 * the caller has actually earned as. Without a statement to look at, every payable party is offered and the server decides.
 */
export function payableParties(earnedAs: readonly string[] | null, payable: readonly string[]): string[] {
  if (earnedAs === null) { return [...payable]; }
  return payable.filter((party) => party === 'OWNER' || earnedAs.includes(party));
}

export function payoutFormError(form: PayoutForm): string | null {
  if (!form.franchiseId) { return 'accessManagement.myPayouts.errors.franchise'; }
  if (!form.party) { return 'accessManagement.myPayouts.errors.party'; }
  if (form.amount === null || !Number.isFinite(form.amount) || form.amount <= 0) { return 'accessManagement.myPayouts.errors.amount'; }
  if (!/^[A-Za-z]{3}$/.test(form.currency)) { return 'accessManagement.myPayouts.errors.currency'; }
  return null;
}

/** The body of a valid form: the amount goes as `{ code, value }` in the chosen currency (no separate currency field). */
export function toPayoutBody(form: PayoutForm): PayoutRequestBody {
  const body: PayoutRequestBody = { franchiseId: form.franchiseId, party: form.party as CommissionParty, amount: hqAmount(Number(form.amount), form.currency) };
  const appCode = form.appCode?.trim();
  const note = form.note?.trim();
  if (appCode) { body.appCode = appCode.toUpperCase(); }
  if (note) { body.note = note; }
  return body;
}

/** The currency of a statement summary row: the one inside its amounts ('' when an older answer names none). */
export function summaryCurrency(summary: StatementSummary): string {
  return hqAmountCode(summary.gross) || hqAmountCode(summary.net) || hqAmountCode(summary.gst);
}

/** True when the zero-based page is the last one for {@code total} rows at {@code size} per page. */
export function isLastPage(page: number, size: number, total: number): boolean {
  return (page + 1) * size >= total;
}


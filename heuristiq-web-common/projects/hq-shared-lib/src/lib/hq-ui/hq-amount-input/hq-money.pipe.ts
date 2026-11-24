import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';
import { HQ_AMOUNT_DEFAULT_CURRENCY, HQ_AMOUNT_FALLBACK_CURRENCY } from './hq-amount-input.constants';
import { HqAmount, hqMoneyText, hqNormalizeCurrency } from './hq-amount.util';
import { hqReadSetting } from '../hq-app-setting';

/**
 * Shows an amount with its currency's symbol and decimal places, in the app's locale: `{{ fee | hqMoney }}` for a
 * `{ code, value }` amount, or `{{ total | hqMoney: 'INR' }}` for a bare number and its currency. The currency falls back to
 * HQ_AMOUNT_DEFAULT_CURRENCY, then INR. Empty when there is no value, or `{{ x | hqMoney: code : '—' }}` for a placeholder.
 */
@Pipe({ name: 'hqMoney', standalone: false })
export class HqMoneyPipe implements PipeTransform {
  private readonly locale: string = inject(LOCALE_ID);
  private readonly appCurrencySetting = inject(HQ_AMOUNT_DEFAULT_CURRENCY, { optional: true });

  transform(amount: HqAmount | number | string | null | undefined, code?: string | null, empty = ''): string {
    const isAmount = amount !== null && typeof amount === 'object';
    const raw = isAmount ? (amount as HqAmount).value : (amount as number | string | null | undefined);
    const value = typeof raw === 'string' ? Number(raw) : raw;
    if (value === null || value === undefined || raw === '' || !Number.isFinite(value)) {
      return empty;
    }
    const currency = hqNormalizeCurrency(isAmount ? (amount as HqAmount).code : code) || hqNormalizeCurrency(code)
      || hqNormalizeCurrency(hqReadSetting(this.appCurrencySetting, HQ_AMOUNT_FALLBACK_CURRENCY)) || HQ_AMOUNT_FALLBACK_CURRENCY;
    return hqMoneyText(value, currency, this.locale);
  }
}

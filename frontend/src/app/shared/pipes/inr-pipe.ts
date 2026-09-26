import { Pipe, PipeTransform } from '@angular/core';
import { formatInr, formatPaise } from '../../core/util/money';

/**
 * Indian Rupee formatting via `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })`.
 *
 * `{{ 1250 | inr }}` → ₹1,250.00 · `{{ 1250 | inr: 'whole' }}` → ₹1,250 · `{{ 54000 | inr: 'paise' }}` → ₹540.00
 */
@Pipe({ name: 'inr', standalone: false })
export class InrPipe implements PipeTransform {
  transform(value: number | null | undefined, mode: 'rupees' | 'whole' | 'paise' = 'rupees'): string {
    if (value === null || value === undefined || Number.isNaN(value)) return '';
    if (mode === 'paise') return formatPaise(value);
    return formatInr(value, { whole: mode === 'whole' });
  }
}

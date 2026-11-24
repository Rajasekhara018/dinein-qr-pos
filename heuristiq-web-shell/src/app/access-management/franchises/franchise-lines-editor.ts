import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { PACKAGE_MAX_LINES, PACKAGE_PARTIES, PACKAGE_VALUE_TYPES, VALUE_TYPE_LABEL_KEYS } from './franchise-packages.constants';
import { PackageLineForm, emptyLineForm } from './franchise-packages.helpers';
import { PARTY_LABEL_KEYS } from '../wallet-statement/wallet-statement.constants';
import { labelKey } from './franchise-earnings.helpers';

/**
 * Edits a list of commission lines in place (party, percentage or fixed value, bounds). Used by the explicit commission of the
 * open-franchise form. The server checks the lines as a commission plan (the sum, the agent bounds) and its message is shown as given.
 */
@Component({
  selector: 'app-franchise-lines-editor',
  standalone: false,
  templateUrl: './franchise-lines-editor.html',
  styleUrl: './franchises-list.scss',
})
export class FranchiseLinesEditor {
  @Input({ required: true }) lines: PackageLineForm[] = [];
  /** Distinguishes the field names when several editors sit in one form. */
  /** Currency of the amounts (the package's or the franchise's); the field shows its symbol and decimal places. */
  @Input() currency = '';
  @Input() idPrefix = 'line';

  readonly parties = PACKAGE_PARTIES;
  readonly valueTypes = PACKAGE_VALUE_TYPES;

  constructor(private translate: TranslateService) {}

  get canAdd(): boolean {
    return this.lines.length < PACKAGE_MAX_LINES;
  }

  add(): void {
    this.lines.push(emptyLineForm());
  }

  remove(index: number): void {
    this.lines.splice(index, 1);
  }

  partyLabel(code: string): string {
    const key = labelKey(PARTY_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }

  valueTypeLabel(code: string): string {
    const key = labelKey(VALUE_TYPE_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }
}

import { Component, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { PACKAGE_VALUE_TYPES, VALUE_TYPE_LABEL_KEYS } from './franchise-packages.constants';
import { PackageReferralForm } from './franchise-packages.helpers';
import { labelKey } from './franchise-earnings.helpers';

/** Edits one referral rate in place (percentage or fixed, bounds, the most referrals one member may earn on). */
@Component({
  selector: 'app-franchise-referral-editor',
  standalone: false,
  templateUrl: './franchise-referral-editor.html',
  styleUrl: './franchises-list.scss',
})
export class FranchiseReferralEditor {
  @Input({ required: true }) referral!: PackageReferralForm;
  /** Currency of the amounts (the package's or the franchise's); the field shows its symbol and decimal places. */
  @Input() currency = '';
  @Input() idPrefix = 'referral';

  readonly valueTypes = PACKAGE_VALUE_TYPES;

  constructor(private translate: TranslateService) {}

  valueTypeLabel(code: string): string {
    const key = labelKey(VALUE_TYPE_LABEL_KEYS, code);
    return key ? this.translate.instant(key) : code;
  }
}

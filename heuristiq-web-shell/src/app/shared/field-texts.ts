import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqFieldTexts } from 'hq-shared-lib';

/** The words hq-shared-lib's field shows by itself, in the user's language (translation keys below). */
export function fieldTexts(): HqFieldTexts {
  const t = inject(TranslateService);
  return {
    showPassword: () => t.instant('shared.field.showPassword'),
    hidePassword: () => t.instant('shared.field.hidePassword'),
    counter: (length, max) => t.instant('shared.field.counter', { length, max }),
    pastDate: () => t.instant('shared.field.pastDate'),
  };
}

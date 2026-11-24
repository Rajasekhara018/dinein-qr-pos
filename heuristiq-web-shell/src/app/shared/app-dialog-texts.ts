import { inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { HqAppDialogTexts } from 'hq-shared-lib';

/** The words hq-shared-lib's confirm/prompt dialog shows, in the user's language (translation keys below). */
export function appDialogTexts(): HqAppDialogTexts {
  const t = inject(TranslateService);
  return {
    required: (label) => t.instant('shared.appDialog.fieldRequired', { label: label }),
    invalidNumber: (label) => t.instant('shared.appDialog.invalidNumber', { label: label }),
    min: (label, min) => t.instant('shared.appDialog.min', { label: label, min }),
    max: (label, max) => t.instant('shared.appDialog.max', { label: label, max }),
    cancel: () => t.instant('shared.appDialog.defaultCancel'),
    confirm: () => t.instant('shared.appDialog.defaultConfirm'),
    working: () => t.instant('shared.appDialog.defaultLoading'),
  };
}

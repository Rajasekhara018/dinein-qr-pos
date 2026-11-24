import { TranslateService } from '@ngx-translate/core';
import { apiErrorMessage } from '../../shared/utils/api-error.helpers';
import { HqLegalProfileError } from 'hq-shared-lib';
import { LEGAL_PROFILE_ERROR_PREFIX, LEGAL_PROFILE_ISSUE_LABEL_KEYS, LEGAL_PROFILE_STALE_STATUS } from './legal-profile.constants';

/** The names (translated, once each, in the order found) of the fields the validation rules still refuse. */
export function legalProfileIssueLabels(errors: readonly HqLegalProfileError[], translate: TranslateService): string[] {
  const labels: string[] = [];
  for (const error of errors) {
    const key = LEGAL_PROFILE_ISSUE_LABEL_KEYS[error.field.split('.').pop() ?? ''];
    const label = key ? translate.instant(key) : '';
    if (label && !labels.includes(label)) labels.push(label);
  }
  return labels;
}

/** The translation key of a service message key (`legalProfile.invalidTaxId` → `legalProfile.errors.invalidTaxId`). */
export function legalProfileErrorKey(messageKey: string): string {
  return `${LEGAL_PROFILE_ERROR_PREFIX}errors.${messageKey.slice(LEGAL_PROFILE_ERROR_PREFIX.length)}`;
}

/**
 * What to tell the person when a save fails: that someone else changed the profile (409), the translated rule the service
 * names, or the service's own message / the general failure text.
 */
export function legalProfileSaveError(err: unknown, translate: TranslateService): string {
  const status = (err as { status?: unknown } | null)?.status;
  if (status === LEGAL_PROFILE_STALE_STATUS) return translate.instant('legalProfile.errors.staleVersion');
  const message = apiErrorMessage(err, translate.instant('legalProfile.messages.saveFailed'));
  return message.startsWith(LEGAL_PROFILE_ERROR_PREFIX) ? translate.instant(legalProfileErrorKey(message)) : message;
}

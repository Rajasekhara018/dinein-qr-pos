import { inject } from '@angular/core';
import { HqTimezoneService } from 'hq-shared-lib';
import { TranslateService } from '@ngx-translate/core';

import { NotificationService } from '../../shared/services/notification-service';
import {
  AUDIT_FAILED_UPLOADS_QUERY,
  AUDIT_FAILED_UPLOADS_ROUTE,
  LIFECYCLE_PRIVILEGES,
  LIFECYCLE_REASON_LABEL_KEYS,
  LIFECYCLE_ROUTE,
  LIFECYCLE_STATUS_LABEL_KEYS,
} from './lifecycle.constants';
import { actionVisual, formatBytes, formatDate, formatDateTime, labelFor, relativeTime, statusTone } from './lifecycle.helpers';
import { LifecycleActionVisual, LifecycleTone } from './lifecycle.models';

/** Display helpers the three lifecycle screens share (formatting, labels, copy to clipboard). */
export abstract class LifecycleViewBase {
  protected readonly translate = inject(TranslateService);
  protected readonly notify = inject(NotificationService);
  private readonly hqTimezone = inject(HqTimezoneService);

  readonly lifecycleRoute = LIFECYCLE_ROUTE;
  readonly auditRoute = AUDIT_FAILED_UPLOADS_ROUTE;
  readonly auditFailedQuery = AUDIT_FAILED_UPLOADS_QUERY;
  readonly privileges = LIFECYCLE_PRIVILEGES;

  protected get lang(): string {
    return this.translate.currentLang || this.translate.defaultLang || 'en';
  }

  size(bytes: number | null | undefined): string {
    return formatBytes(bytes);
  }

  absolute(iso: string | null | undefined): string {
    return formatDateTime(iso, this.lang, this.hqTimezone.effectiveTimezone());
  }

  day(iso: string | null | undefined): string {
    return formatDate(iso, this.lang, this.hqTimezone.effectiveTimezone());
  }

  relative(iso: string | null | undefined): string {
    return relativeTime(iso, new Date(), this.lang);
  }

  tone(status: string | null | undefined): LifecycleTone {
    return statusTone(status);
  }

  statusLabel(code: string | null | undefined): string {
    return labelFor(LIFECYCLE_STATUS_LABEL_KEYS, code, (k) => this.translate.instant(k));
  }

  reasonLabel(code: string | null | undefined): string {
    return labelFor(LIFECYCLE_REASON_LABEL_KEYS, code, (k) => this.translate.instant(k));
  }

  visual(action: string | null | undefined): LifecycleActionVisual {
    return actionVisual(action);
  }

  actionLabel(action: string | null | undefined): string {
    const v = actionVisual(action);
    return v.labelKey ? this.translate.instant(v.labelKey) : (action ?? '');
  }

  /** Copies text and says so; never throws. */
  async copy(text: string | null | undefined): Promise<void> {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      this.notify.success(this.translate.instant('documentManagement.lifecycle.copied'));
    } catch {
      this.notify.error(this.translate.instant('documentManagement.lifecycle.copyFailed'));
    }
  }
}

import { Component, Input } from '@angular/core';
import { Observable } from 'rxjs';
import { HqToast, HqToastService, HqToastType } from '../hq-toast.service';

/** Renders the toasts raised through HqNotificationService. Place one in the app shell. */
@Component({
  selector: 'hq-toast-host',
  templateUrl: './hq-toast-host.html',
  styleUrl: './hq-toast-host.scss',
  standalone: false,
})
export class HqToastHost {
  /** Accessible label of each toast's close button; pass the app's translated text. */
  @Input() dismissLabel = 'Dismiss';

  toasts$: Observable<HqToast[]>;

  constructor(private service: HqToastService) {
    this.toasts$ = this.service.toasts$;
  }

  dismiss(id: number): void {
    this.service.remove(id);
  }

  trackById(_: number, t: HqToast): number {
    return t.id;
  }

  icon(type: HqToastType): string {
    const map: Record<HqToastType, string> = {
      success: 'bi-check-circle-fill',
      error:   'bi-x-circle-fill',
      warning: 'bi-exclamation-triangle-fill',
      info:    'bi-info-circle-fill',
    };
    return map[type];
  }
}

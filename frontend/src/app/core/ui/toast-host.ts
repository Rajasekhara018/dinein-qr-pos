import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Toast, ToastService } from './toast.service';

/** Renders app-wide toasts. Place exactly once, in the root component template. */
@Component({
  selector: 'app-toast-host',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './toast-host.html',
})
export class ToastHost {
  protected readonly toasts = inject(ToastService);

  protected kindClasses(toast: Toast): string {
    switch (toast.kind) {
      case 'success':
        return 'bg-green-50 text-green-900 ring-green-200 dark:bg-green-950 dark:text-green-100 dark:ring-green-800';
      case 'error':
        return 'bg-red-50 text-red-900 ring-red-200 dark:bg-red-950 dark:text-red-100 dark:ring-red-800';
      case 'warning':
        return 'bg-amber-50 text-amber-950 ring-amber-200 dark:bg-amber-950 dark:text-amber-100 dark:ring-amber-800';
      default:
        return 'bg-surface text-ink ring-line';
    }
  }

  protected runAction(toast: Toast): void {
    toast.action?.run();
    this.toasts.dismiss(toast.id);
  }
}

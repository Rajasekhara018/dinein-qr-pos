import { Injectable } from '@angular/core';
import { HqToastService } from './hq-toast.service';

/** Raises a toast: success, error, warning or info, each with its own default duration in milliseconds. */
@Injectable({ providedIn: 'root' })
export class HqNotificationService {
  constructor(private toast: HqToastService) {}

  success(message: string, duration = 3000): void {
    this.toast.add('success', message, duration);
  }

  error(message: string, duration = 5000): void {
    this.toast.add('error', message, duration);
  }

  warning(message: string, duration = 4000): void {
    this.toast.add('warning', message, duration);
  }

  info(message: string, duration = 3000): void {
    this.toast.add('info', message, duration);
  }
}

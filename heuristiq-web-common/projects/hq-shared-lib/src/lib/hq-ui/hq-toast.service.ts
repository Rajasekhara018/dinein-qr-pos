import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type HqToastType = 'success' | 'error' | 'warning' | 'info';

export interface HqToast {
  id: number;
  type: HqToastType;
  message: string;
  duration: number;
}

/** The stack of toast messages shown by `<hq-toast-host>`. Use HqNotificationService to raise them. */
@Injectable({ providedIn: 'root' })
export class HqToastService {
  private _id = 0;
  private _toasts$ = new BehaviorSubject<HqToast[]>([]);
  readonly toasts$ = this._toasts$.asObservable();

  add(type: HqToastType, message: string, duration: number): void {
    const id = ++this._id;
    this._toasts$.next([...this._toasts$.value, { id, type, message, duration }]);
    setTimeout(() => this.remove(id), duration);
  }

  remove(id: number): void {
    this._toasts$.next(this._toasts$.value.filter(t => t.id !== id));
  }
}

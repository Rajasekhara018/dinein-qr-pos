import { CanDeactivateFn } from '@angular/router';
import { Observable } from 'rxjs';

export interface PendingChangesAware {
  hasPendingChanges(): boolean;
  confirmDiscardChanges(): boolean | Promise<boolean> | Observable<boolean>;
}

export const pendingChangesGuard: CanDeactivateFn<PendingChangesAware> = (component) => {
  if (!component.hasPendingChanges()) return true;
  return component.confirmDiscardChanges();
};

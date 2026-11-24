import { CanDeactivateFn } from '@angular/router';
import { Observable } from 'rxjs';

/** A routed component that may hold unsaved edits; paired with hqPendingChangesGuard. */
export interface HqPendingChangesAware {
  hasPendingChanges(): boolean;
  confirmDiscardChanges(): boolean | Promise<boolean> | Observable<boolean>;
}

/** CanDeactivate guard: lets navigation through unless the component reports pending changes, then asks it to confirm. */
export const hqPendingChangesGuard: CanDeactivateFn<HqPendingChangesAware> = (component) => {
  if (!component.hasPendingChanges()) return true;
  return component.confirmDiscardChanges();
};

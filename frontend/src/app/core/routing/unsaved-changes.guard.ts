import { CanDeactivateFn } from '@angular/router';

/** A routed component can implement this to be asked before navigating away with unsaved changes. */
export interface CanComponentDeactivate {
  canDeactivate(): boolean | Promise<boolean>;
}

/**
 * Generic `canDeactivate` guard: defers to the component's own `canDeactivate()` (e.g. "Discard unsaved changes?"
 * via `ConfirmService`) when it has one, otherwise allows navigation. Attach to any route whose component edits a
 * form that shouldn't be lost silently (see `catalog-routing-module.ts`'s `items/:id` and `items/new`).
 */
export const unsavedChangesGuard: CanDeactivateFn<CanComponentDeactivate> = (component) =>
  component.canDeactivate ? component.canDeactivate() : true;

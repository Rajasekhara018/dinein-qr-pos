import { Dialog, DialogConfig, DialogRef } from '@angular/cdk/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { ComponentType } from '@angular/cdk/portal';
import { inject, Injectable } from '@angular/core';
import { BreakpointService } from './breakpoint.service';

export interface SheetOptions<D> {
  data?: D;
  /** Accessible name when the content has no heading referenced via `ariaLabelledBy`. */
  ariaLabel?: string;
  ariaLabelledBy?: string;
  /** Max width of the centered dialog on ≥ 768px. Default `32rem`. */
  maxWidth?: string;
  disableClose?: boolean;
  /** Force a presentation instead of choosing by breakpoint. */
  presentation?: 'sheet' | 'dialog';
}

/**
 * Opens a CDK dialog as a full-width bottom sheet on phones (< 768px) and as a centered dialog on larger screens.
 * The opened component should render its chrome with `<app-bottom-sheet>` (SharedModule), which adapts to both.
 * Inject `DIALOG_DATA` and `DialogRef` from `@angular/cdk/dialog` inside the component.
 */
@Injectable({ providedIn: 'root' })
export class SheetService {
  private readonly dialog = inject(Dialog);
  private readonly overlay = inject(Overlay);
  private readonly breakpoints = inject(BreakpointService);

  open<R = unknown, D = unknown, C = unknown>(
    component: ComponentType<C>,
    options: SheetOptions<D> = {},
  ): DialogRef<R, C> {
    const asSheet = (options.presentation ?? (this.breakpoints.isHandset() ? 'sheet' : 'dialog')) === 'sheet';
    const config: DialogConfig<D, DialogRef<R, C>> = {
      data: options.data,
      ariaLabel: options.ariaLabel ?? null,
      ariaLabelledBy: options.ariaLabelledBy ?? null,
      disableClose: options.disableClose ?? false,
      autoFocus: 'first-heading',
      restoreFocus: true,
      hasBackdrop: true,
      backdropClass: 'app-dialog-backdrop',
      panelClass: ['app-sheet-pane', asSheet ? 'app-sheet-pane--sheet' : 'app-sheet-pane--dialog'],
      width: asSheet ? '100%' : `min(${options.maxWidth ?? '32rem'}, calc(100vw - 2rem))`,
      maxWidth: '100vw',
      maxHeight: asSheet ? '92dvh' : 'calc(100dvh - 4rem)',
      positionStrategy: asSheet
        ? this.overlay.position().global().bottom('0').centerHorizontally()
        : this.overlay.position().global().centerHorizontally().centerVertically(),
      scrollStrategy: this.overlay.scrollStrategies.block(),
      closeOnNavigation: true,
    };
    return this.dialog.open<R, D, C>(component, config);
  }
}

import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  DOCUMENT,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { KitchenOrderView } from '../../../core/api/models';
import { KITCHEN_PATHS } from '../../../core/auth/auth-paths';
import { DeviceAuthStore } from '../../../core/auth/device-auth.store';
import { ConfirmService } from '../../../shared/services/confirm.service';
import { COLUMN_LABELS, KITCHEN_COLUMNS, KitchenColumn } from '../data/board-state';
import { KitchenBoardStore } from '../data/kitchen-board.store';
import { KitchenClock } from '../data/kitchen-clock';
import { KitchenSound } from '../data/kitchen-sound';
import { FullscreenControl, ScreenWakeLock } from '../data/screen-controls';

/**
 * `/kitchen`: live order board. Owns (via component providers) the 1 s ticker, the board store with its STOMP
 * subscription, the wake lock and the fullscreen listener, so all of them are torn down when the board is destroyed
 * (sign-out, or a 401 from a revoked device that sends the user to `/kitchen/login`).
 */
@Component({
  selector: 'app-kitchen-board',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [KitchenClock, KitchenBoardStore, ScreenWakeLock, FullscreenControl],
  host: {
    class: 'flex min-h-dvh flex-col md:h-dvh md:overflow-hidden',
    '(document:pointerdown)': 'onUserGesture()',
    '(document:keydown)': 'onUserGesture()',
  },
  templateUrl: './kitchen-board.html',
})
export class KitchenBoard {
  protected readonly store = inject(KitchenBoardStore);
  protected readonly clock = inject(KitchenClock);
  protected readonly sound = inject(KitchenSound);
  private readonly wakeLock = inject(ScreenWakeLock);
  private readonly fullscreen = inject(FullscreenControl);
  private readonly devices = inject(DeviceAuthStore);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly confirmSvc = inject(ConfirmService);

  protected readonly columns = KITCHEN_COLUMNS;
  protected readonly labels = COLUMN_LABELS;
  /** Phone layout (<768px): which column tab is shown. */
  protected readonly tab = signal<KitchenColumn>('CONFIRMED');

  constructor() {
    this.store.start();
    void this.wakeLock.acquire();
    void this.sound.probe();

    this.store.arrived$.pipe(takeUntilDestroyed()).subscribe(() => void this.sound.newOrder());

    inject(DestroyRef).onDestroy(() => void this.store.stopRealtime());
  }

  protected advance(order: KitchenOrderView): void {
    void this.store.advance(order);
  }

  protected togglePriority(order: KitchenOrderView): void {
    void this.store.setPriority(order);
  }

  protected onUserGesture(): void {
    void this.sound.resumeFromGesture();
  }

  protected enableSound(): void {
    void this.sound.enable();
  }

  protected dismissSound(): void {
    this.sound.disable();
  }

  protected selectTab(column: KitchenColumn): void {
    this.tab.set(column);
  }

  protected onTabKeydown(event: KeyboardEvent, index: number): void {
    const delta = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (!delta) return;
    event.preventDefault();
    const next = this.columns[(index + delta + this.columns.length) % this.columns.length];
    this.tab.set(next);
    this.document.getElementById(`tab-${next}`)?.focus();
  }

  async signOut(): Promise<void> {
    const result = await this.confirmSvc.confirm({
      title: 'Sign out this screen?',
      message:
        'Sign out this kitchen screen? You will need a username and PIN or password to sign in again.',
      confirmLabel: 'Sign out',
      tone: 'primary',
    });
    if (!result) return;
    // Don't wait for the socket to close (deactivate can take a while on a flaky network).
    void this.store.stopRealtime();
    this.fullscreen.exit();
    this.devices.clear();
    await this.router.navigateByUrl(KITCHEN_PATHS.login);
  }
}

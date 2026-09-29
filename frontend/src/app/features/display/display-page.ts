import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { PublicApi } from '../../core/api/public.api';
import { DisplayEvent, TOPICS } from '../../core/api/models';
import { RealtimeService } from '../../core/realtime/realtime.service';

/**
 * `/display?r=<restaurantId>`: unattended, login-free screen for the dining area — "Preparing: #102 #105" /
 * "Ready: #099 #101". No guest data beyond the display token a guest was already given at checkout.
 */
@Component({
  selector: 'app-display-page',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './display-page.html',
})
export class DisplayPage {
  private readonly api = inject(PublicApi);
  private readonly realtime = inject(RealtimeService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly preparing = signal<number[]>([]);
  protected readonly ready = signal<number[]>([]);
  protected readonly error = signal(false);
  protected readonly loading = signal(true);

  private restaurantId: number | null = null;

  constructor() {
    const raw = this.route.snapshot.queryParamMap.get('r');
    this.restaurantId = raw ? Number(raw) : null;
    if (!this.restaurantId || Number.isNaN(this.restaurantId)) {
      this.error.set(true);
      this.loading.set(false);
      return;
    }
    void this.load();
    this.realtime
      .watch<DisplayEvent>(TOPICS.display(this.restaurantId))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.onEvent(event));
    this.realtime.connected$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => void this.load());
  }

  private async load(): Promise<void> {
    if (!this.restaurantId) return;
    try {
      const board = await firstValueFrom(this.api.displayBoard(this.restaurantId));
      this.preparing.set(board.preparing);
      this.ready.set(board.ready);
      this.error.set(false);
    } catch {
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }

  private onEvent(event: DisplayEvent): void {
    const token = event.displayToken;
    switch (event.status) {
      case 'PREPARING':
        this.ready.update((list) => list.filter((t) => t !== token));
        this.preparing.update((list) => (list.includes(token) ? list : [...list, token]));
        break;
      case 'READY':
        this.preparing.update((list) => list.filter((t) => t !== token));
        this.ready.update((list) => (list.includes(token) ? list : [...list, token]));
        break;
      default:
        // COMPLETED / CANCELLED: gone from the board either way.
        this.preparing.update((list) => list.filter((t) => t !== token));
        this.ready.update((list) => list.filter((t) => t !== token));
        break;
    }
  }
}

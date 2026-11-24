import { ChangeDetectionStrategy, Component, computed, inject, output } from '@angular/core';
import { DeviceAuthStore } from '../../../core/auth/device-auth.store';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { KitchenBoardStore } from '../data/kitchen-board.store';
import { formatIstClock, KitchenClock } from '../data/kitchen-clock';
import { KitchenPrefs } from '../data/kitchen-prefs';
import { KitchenSound } from '../data/kitchen-sound';
import { FullscreenControl } from '../data/screen-controls';

/** Board header: restaurant, IST clock, counts, connection status and screen controls. */
@Component({
  selector: 'app-board-header',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block border-b border-line bg-surface' },
  templateUrl: './board-header.html',
})
export class BoardHeader {
  protected readonly store = inject(KitchenBoardStore);
  protected readonly sound = inject(KitchenSound);
  protected readonly prefs = inject(KitchenPrefs);
  protected readonly fullscreen = inject(FullscreenControl);
  private readonly clock = inject(KitchenClock);
  private readonly realtime = inject(RealtimeService);
  private readonly devices = inject(DeviceAuthStore);

  readonly signOut = output<void>();

  protected readonly clockText = computed(() => formatIstClock(this.clock.now()));
  protected readonly deviceLabel = computed(() => {
    const session = this.devices.session();
    return session?.deviceName || session?.user.displayName || session?.user.username || '';
  });

  protected readonly connection = computed(() => {
    switch (this.realtime.connectionState()) {
      case 'connected':
        return {
          label: 'Live',
          dot: 'bg-emerald-500',
          text: 'text-emerald-700 dark:text-emerald-300',
        };
      case 'connecting':
        return {
          label: 'Connecting…',
          dot: 'bg-amber-500 animate-pulse',
          text: 'text-amber-700 dark:text-amber-300',
        };
      case 'reconnecting':
        return {
          label: 'Reconnecting…',
          dot: 'bg-amber-500 animate-pulse',
          text: 'text-amber-700 dark:text-amber-300',
        };
      default:
        return { label: 'Offline', dot: 'bg-red-500', text: 'text-red-700 dark:text-red-300' };
    }
  });

  protected toggleSound(): void {
    if (this.sound.enabled()) this.sound.disable();
    else void this.sound.enable();
  }
}

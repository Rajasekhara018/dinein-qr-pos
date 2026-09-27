import { ChangeDetectionStrategy, Component, computed, inject, output } from '@angular/core';
import { AuthStore } from '../../../core/auth/auth.store';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { WaiterAlerts } from '../data/waiter-alerts';
import { WaiterBoardStore } from '../data/waiter-board.store';
import { WaiterPrefs } from '../data/waiter-prefs';

/** Waiter header: restaurant, signed-in staff member, live connection, notifications, sound and sign out. */
@Component({
  selector: 'app-waiter-header',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block border-b border-line bg-surface' },
  templateUrl: './waiter-header.html',
})
export class WaiterHeader {
  protected readonly board = inject(WaiterBoardStore);
  protected readonly alerts = inject(WaiterAlerts);
  protected readonly prefs = inject(WaiterPrefs);
  private readonly auth = inject(AuthStore);
  private readonly realtime = inject(RealtimeService);

  readonly signOut = output<void>();

  protected readonly restaurantName = computed(
    () => this.board.config()?.restaurantName ?? 'Waiter',
  );
  protected readonly staffName = computed(() => {
    const user = this.auth.user();
    if (!user) return '';
    const name = user.displayName || user.username;
    return user.role === 'WAITER' ? name : `${name} (${user.role === 'OWNER' ? 'owner' : 'manager'})`;
  });

  protected readonly connection = computed(() => {
    switch (this.realtime.connectionState()) {
      case 'connected':
        return { label: 'Live', dot: 'bg-emerald-500', text: 'text-emerald-700 dark:text-emerald-300' };
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
}

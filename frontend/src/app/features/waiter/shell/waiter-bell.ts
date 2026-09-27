import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ConnectedPosition } from '@angular/cdk/overlay';
import { NotificationView } from '../../../core/api/models';
import { WaiterNotificationsStore } from '../data/waiter-notifications.store';

/** Waiter header bell: unread badge + panel with the latest "order ready" notifications (mark read / read all). */
@Component({
  selector: 'app-waiter-bell',
  standalone: false,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'relative inline-flex' },
  templateUrl: './waiter-bell.html',
})
export class WaiterBell {
  protected readonly store = inject(WaiterNotificationsStore);
  protected readonly open = signal(false);

  protected readonly badge = computed(() => {
    const n = this.store.unread();
    return n > 99 ? '99+' : String(n);
  });
  protected readonly label = computed(() => {
    const n = this.store.unread();
    return n > 0 ? `Notifications, ${n} unread` : 'Notifications';
  });

  protected readonly positions: ConnectedPosition[] = [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
    { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: 6 },
  ];

  protected toggle(): void {
    const next = !this.open();
    this.open.set(next);
    if (next) void this.store.load();
  }

  protected close(): void {
    this.open.set(false);
  }

  protected onOutside(event: MouseEvent, trigger: HTMLElement): void {
    if (!trigger.contains(event.target as Node)) this.close();
  }

  protected async select(notification: NotificationView): Promise<void> {
    this.close();
    await this.store.open(notification);
  }
}
